package cl.duoc.pedidos360.report;

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
 * Consumidor de {@code orders.events}. Recibe el DTO deserializado y validado y delega en {@link ProjectionService}.
 * Grupo propio (pedidos360-report) y 3 hilos: cada uno toma una partición.
 */
@Component
public class OrderEventListener {
    private static final Logger log = LoggerFactory.getLogger(OrderEventListener.class);
    private final ProjectionService projections;

    public OrderEventListener(ProjectionService projections) {
        this.projections = projections;
    }

    @KafkaListener(topics = KafkaTopics.ORDER_EVENTS, groupId = "${app.kafka.group-id}",
            concurrency = "${app.kafka.concurrency:3}")
    public void onEvent(@Payload @Valid OrderEvent event,
                        @Header(KafkaHeaders.RECEIVED_PARTITION) int partition,
                        @Header(KafkaHeaders.OFFSET) long offset) {
        boolean changed = projections.apply(event);
        log.info("Proyección {} partition={} offset={} type={} pedido={} version={}", changed ? "actualizada" : "sin cambios (duplicado o antiguo)",
                partition, offset, event.type(), event.order().id(), event.order().version());
    }
}
