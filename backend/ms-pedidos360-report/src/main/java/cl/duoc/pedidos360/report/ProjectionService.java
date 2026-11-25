package cl.duoc.pedidos360.report;

import cl.duoc.pedidos360.common.Store;
import cl.duoc.pedidos360.common.kafka.OrderEvent;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Lógica de dominio de reportes, independiente de Kafka: mantiene una proyección por pedido.
 * Usa la versión del pedido para ignorar duplicados y eventos antiguos (idempotencia).
 */
@Service
@Transactional
public class ProjectionService {
    private final Store store;

    public ProjectionService(Store store) {
        this.store = store;
    }

    /** @return true si la proyección cambió; false si el evento era duplicado o antiguo. */
    public boolean apply(OrderEvent event) {
        var order = event.order();
        String key = "projection:" + order.id();
        if (store.exists(key)) {
            var previous = store.lock(key);
            if (previous.path("version").asLong() >= order.version()) return false;
            store.put(key, "projection", store.toTree(order));
        } else {
            store.insert(key, "projection", store.toTree(order));
        }
        return true;
    }
}
