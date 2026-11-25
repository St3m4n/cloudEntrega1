package cl.duoc.pedidos360.orders;

import cl.duoc.pedidos360.common.kafka.KafkaTopics;
import cl.duoc.pedidos360.common.kafka.OrderEvent;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.validation.Validator;
import java.util.concurrent.TimeUnit;
import org.apache.kafka.clients.producer.RecordMetadata;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

/**
 * Productor Kafka de Pedidos360. Convierte el mensaje guardado en el outbox al DTO {@link OrderEvent},
 * lo valida y lo publica con {@code JsonSerializer}. La clave es el id del pedido (correlationId), por lo que
 * todos los eventos de un pedido van a la misma partición y conservan su orden.
 */
@Component
public class OrderEventPublisher {
    private static final Logger log = LoggerFactory.getLogger(OrderEventPublisher.class);
    private final KafkaTemplate<String, OrderEvent> kafka;
    private final ObjectMapper mapper;
    private final Validator validator;

    public OrderEventPublisher(KafkaTemplate<String, OrderEvent> kafka, ObjectMapper mapper, Validator validator) {
        this.kafka = kafka;
        this.mapper = mapper;
        this.validator = validator;
    }

    public static boolean handles(String destination) {
        return KafkaTopics.MAIN.contains(destination);
    }

    /** Publica y espera la confirmación del broker (acks=all); si falla, el outbox reintenta más tarde. */
    public RecordMetadata publish(String topic, JsonNode stored) throws Exception {
        OrderEvent event = mapper.treeToValue(stored, OrderEvent.class);
        var violations = validator.validate(event);
        if (!violations.isEmpty()) throw new IllegalStateException("Evento inválido, no se publica: " + violations);
        var metadata = kafka.send(topic, event.correlationId(), event).get(20, TimeUnit.SECONDS).getRecordMetadata();
        log.info("Evento publicado topic={} partition={} offset={} key={} type={} eventId={}", metadata.topic(),
                metadata.partition(), metadata.offset(), event.correlationId(), event.type(), event.eventId());
        return metadata;
    }
}
