package cl.duoc.pedidos360.common;

import cl.duoc.pedidos360.common.kafka.InvalidEventException;
import cl.duoc.pedidos360.common.kafka.KafkaTopics;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.validation.ValidationException;
import jakarta.validation.Validator;
import java.nio.charset.StandardCharsets;
import java.util.stream.Collectors;
import org.apache.kafka.common.header.Header;
import org.apache.kafka.common.header.internals.RecordHeader;
import org.apache.kafka.common.header.internals.RecordHeaders;
import org.apache.kafka.common.TopicPartition;
import org.apache.kafka.common.errors.SerializationException;
import org.apache.kafka.common.serialization.Serializer;
import org.apache.kafka.common.serialization.StringSerializer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.annotation.KafkaListenerConfigurer;
import org.springframework.kafka.config.KafkaListenerEndpointRegistrar;
import org.springframework.kafka.core.DefaultKafkaProducerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.core.ProducerFactory;
import org.springframework.kafka.listener.DeadLetterPublishingRecoverer;
import org.springframework.kafka.listener.DefaultErrorHandler;
import org.springframework.kafka.support.ExponentialBackOffWithMaxRetries;
import org.springframework.kafka.support.serializer.DeserializationException;
import org.springframework.messaging.handler.annotation.support.MethodArgumentNotValidException;
import org.springframework.validation.beanvalidation.SpringValidatorAdapter;

/**
 * Manejo de errores de los consumidores Kafka (audit y report).
 * <ul>
 *   <li>Valida los DTO recibidos con {@code @Valid} (Bean Validation).</li>
 *   <li>Reintenta fallos transitorios con backoff exponencial (1 s, 2 s, 4 s).</li>
 *   <li>Los errores de formato o validación no se reintentan.</li>
 *   <li>Al agotar los reintentos publica el mensaje original en {@code <tópico>.<aplicación>.DLT}
 *       con cabeceras que indican tópico, partición, offset, grupo y excepción de origen.</li>
 * </ul>
 */
@Configuration
public class StreamingConfig implements KafkaListenerConfigurer {
    public static final String REASON_HEADER = "x-dlt-reason";
    private static final Logger log = LoggerFactory.getLogger(StreamingConfig.class);
    private final Validator validator;

    public StreamingConfig(Validator validator) {
        this.validator = validator;
    }

    @Override
    public void configureKafkaListeners(KafkaListenerEndpointRegistrar registrar) {
        registrar.setValidator(new SpringValidatorAdapter(validator));
    }

    @Bean
    DefaultErrorHandler kafkaErrorHandler(ProducerFactory<?, ?> producerFactory, ObjectMapper mapper,
                                          @Value("${spring.application.name}") String application,
                                          @Value("${app.kafka.retry.attempts:3}") int attempts,
                                          @Value("${app.kafka.retry.initial-interval-ms:1000}") long initialInterval) {
        // Productor propio para el DLT: reenvía bytes originales (JSON mal formado) o el DTO ya leído.
        var deadLetterFactory = new DefaultKafkaProducerFactory<String, Object>(
                producerFactory.getConfigurationProperties(), new StringSerializer(), deadLetterSerializer(mapper));
        var publisher = new DeadLetterPublishingRecoverer(new KafkaTemplate<>(deadLetterFactory),
                (record, error) -> new TopicPartition(KafkaTopics.deadLetterOf(record.topic(), application), record.partition()));
        publisher.setFailIfSendResultIsError(true);
        // Cabecera propia con el motivo legible (campos inválidos o error de JSON), además de las cabeceras kafka_dlt-*.
        publisher.setHeadersFunction((record, error) -> new RecordHeaders(new Header[] {
                new RecordHeader(REASON_HEADER, reason(error).getBytes(StandardCharsets.UTF_8))}));

        var backOff = new ExponentialBackOffWithMaxRetries(attempts);
        backOff.setInitialInterval(initialInterval);
        backOff.setMultiplier(2.0);
        backOff.setMaxInterval(initialInterval * 4);

        var handler = new DefaultErrorHandler((record, error) -> {
            log.error("Evento enviado a DLT topic={} partition={} offset={} key={} dlt={} causa={}", record.topic(),
                    record.partition(), record.offset(), record.key(), KafkaTopics.deadLetterOf(record.topic(), application),
                    reason(error));
            publisher.accept(record, error);
        }, backOff);
        handler.addNotRetryableExceptions(InvalidEventException.class, ValidationException.class);
        handler.setRetryListeners((record, error, attempt) -> log.warn(
                "Entrega fallida intento={} (máx. {} reintentos) topic={} partition={} offset={} causa={}", attempt, attempts,
                record.topic(), record.partition(), record.offset(), reason(error)));
        return handler;
    }

    static Serializer<Object> deadLetterSerializer(ObjectMapper mapper) {
        return (topic, data) -> {
            if (data == null) return null;
            if (data instanceof byte[] bytes) return bytes;
            if (data instanceof String text) return text.getBytes(StandardCharsets.UTF_8);
            try {
                return mapper.writeValueAsBytes(data);
            } catch (JsonProcessingException e) {
                throw new SerializationException("No se pudo serializar el evento para el DLT", e);
            }
        };
    }

    /** Motivo legible del rechazo: campos inválidos del DTO, JSON mal formado o la causa raíz. */
    public static String reason(Throwable error) {
        for (Throwable current = error; current != null; current = current.getCause() == current ? null : current.getCause()) {
            if (current instanceof MethodArgumentNotValidException invalid && invalid.getBindingResult() != null)
                return "DTO inválido: " + invalid.getBindingResult().getFieldErrors().stream()
                        .map(field -> field.getField() + " " + field.getDefaultMessage()).sorted().collect(Collectors.joining("; "));
            if (current instanceof DeserializationException) return "JSON inválido: " + rootCause(current);
        }
        return rootCause(error);
    }

    public static String rootCause(Throwable error) {
        Throwable cause = error;
        while (cause.getCause() != null && cause.getCause() != cause) cause = cause.getCause();
        String message = cause.getClass().getSimpleName() + ": " + cause.getMessage();
        return message.length() > 300 ? message.substring(0, 300) : message;
    }
}
