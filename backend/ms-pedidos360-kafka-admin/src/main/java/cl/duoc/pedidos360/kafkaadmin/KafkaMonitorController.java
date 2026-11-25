package cl.duoc.pedidos360.kafkaadmin;

import cl.duoc.pedidos360.common.kafka.KafkaTopics;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import org.springframework.http.HttpStatus;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

/** Mensajes recientes de los tópicos y DLT para el front, y envío de eventos inválidos para demostrar el DLT. */
@RestController
@RequestMapping("/api/admin/kafka")
public class KafkaMonitorController {
    private final KafkaMessageMonitor monitor;
    private final KafkaTemplate<String, String> kafka;

    public KafkaMonitorController(KafkaMessageMonitor monitor, KafkaTemplate<String, String> kafka) {
        this.monitor = monitor;
        this.kafka = kafka;
    }

    public record TestEventInput(@NotBlank String topic,
                                 @NotBlank @Pattern(regexp = "malformed-json|invalid-event") String kind) {}

    @GetMapping("/messages")
    public Map<String, Object> messages() {
        return Map.of("topics", monitor.snapshot(), "updatedAt", Instant.now().toString());
    }

    /**
     * malformed-json: JSON truncado; falla al deserializar y no se reintenta.
     * invalid-event: JSON válido que no cumple el DTO (id negativo, sin estado ni versión); falla la validación @Valid.
     */
    @PostMapping("/test-events")
    @ResponseStatus(HttpStatus.ACCEPTED)
    public Map<String, Object> send(@Valid @RequestBody TestEventInput input) throws Exception {
        if (!KafkaTopics.MAIN.contains(input.topic()))
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Solo se permiten " + KafkaTopics.MAIN);
        String eventId = UUID.randomUUID().toString();
        String payload = input.kind().equals("malformed-json")
                ? "{\"type\":\"OrderCreated\",\"eventId\":\"" + eventId + "\",\"order\":{\"id\":"
                : "{\"type\":\"OrderCreated\",\"eventId\":\"" + eventId + "\",\"timestamp\":\"" + Instant.now()
                  + "\",\"correlationId\":\"demo-invalido\",\"order\":{\"id\":-1,\"estado\":\"\"}}";
        var metadata = kafka.send(input.topic(), "demo-invalido", payload).get(10, TimeUnit.SECONDS).getRecordMetadata();
        return Map.of("topic", metadata.topic(), "partition", metadata.partition(), "offset", metadata.offset(),
                "kind", input.kind(), "payload", payload);
    }
}
