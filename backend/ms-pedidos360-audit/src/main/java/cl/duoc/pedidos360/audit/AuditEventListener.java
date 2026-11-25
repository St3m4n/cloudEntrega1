package cl.duoc.pedidos360.audit;

import cl.duoc.pedidos360.common.kafka.KafkaTopics;
import cl.duoc.pedidos360.common.kafka.OrderEvent;
import jakarta.validation.Valid;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.KafkaHeaders;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.stereotype.Component;

/**
 * Consumidor de {@code audit.timeline}. Solo adapta el mensaje Kafka: recibe el DTO ya deserializado y validado,
 * y delega en {@link AuditService}. Grupo propio y 3 hilos (uno por partición).
 */
@Component
public class AuditEventListener {
    private static final Logger log = LoggerFactory.getLogger(AuditEventListener.class);
    private final AuditService audit;

    public AuditEventListener(AuditService audit) {
        this.audit = audit;
    }

    @KafkaListener(topics = KafkaTopics.AUDIT_TIMELINE, groupId = "${app.kafka.group-id}",
            concurrency = "${app.kafka.concurrency:3}")
    public void onEvent(@Payload @Valid OrderEvent event,
                        @Header(KafkaHeaders.RECEIVED_PARTITION) int partition,
                        @Header(KafkaHeaders.OFFSET) long offset) {
        boolean stored = audit.record(event);
        log.info("Auditoría {} partition={} offset={} type={} pedido={} eventId={}", stored ? "registrada" : "duplicada ignorada",
                partition, offset, event.type(), event.order().id(), event.eventId());
    }
}
