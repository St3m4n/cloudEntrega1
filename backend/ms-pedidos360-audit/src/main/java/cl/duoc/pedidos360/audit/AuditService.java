package cl.duoc.pedidos360.audit;

import cl.duoc.pedidos360.common.Store;
import cl.duoc.pedidos360.common.kafka.OrderEvent;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Lógica de dominio de auditoría, independiente de Kafka: registra eventos sin duplicados y los consulta. */
@Service
@Transactional
public class AuditService {
    private final Store store;

    public AuditService(Store store) {
        this.store = store;
    }

    /** Guarda el evento una sola vez por eventId (las entregas de Kafka son al menos una vez). */
    public boolean record(OrderEvent event) {
        String key = "audit:" + event.eventId();
        if (store.exists(key)) return false;
        store.insert(key, "audit", store.toTree(event));
        return true;
    }

    @Transactional(readOnly = true)
    public List<ObjectNode> events(String user, String type, Long orderId, Instant from, Instant to) {
        return store.all("audit").stream()
                .filter(e -> user == null || e.path("actor").asText().equals(user) || e.path("order").path("cliente").asText().equals(user))
                .filter(e -> type == null || e.path("type").asText().equals(type))
                .filter(e -> orderId == null || e.path("order").path("id").asLong() == orderId)
                .filter(e -> from == null || !Instant.parse(e.path("timestamp").asText()).isBefore(from))
                .filter(e -> to == null || !Instant.parse(e.path("timestamp").asText()).isAfter(to))
                .sorted(Comparator.comparing(e -> e.path("timestamp").asText()))
                .toList();
    }
}
