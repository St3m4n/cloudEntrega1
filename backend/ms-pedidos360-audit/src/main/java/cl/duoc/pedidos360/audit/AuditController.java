package cl.duoc.pedidos360.audit;

import cl.duoc.pedidos360.common.StreamingConfig;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.time.Instant;
import java.util.List;
import org.springframework.context.annotation.Import;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** API REST de solo lectura de la auditoría. El consumo Kafka está en {@link AuditEventListener}. */
@RestController
@RequestMapping("/api/audit")
@Import(StreamingConfig.class)
public class AuditController {
    private final AuditService audit;

    public AuditController(AuditService audit) {
        this.audit = audit;
    }

    @GetMapping({"", "/events"})
    public List<ObjectNode> events(@RequestParam(required = false) String user, @RequestParam(required = false) String type,
                                   @RequestParam(required = false) Long orderId, @RequestParam(required = false) Instant from,
                                   @RequestParam(required = false) Instant to) {
        return audit.events(user, type, orderId, from, to);
    }
}
