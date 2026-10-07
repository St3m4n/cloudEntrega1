package cl.duoc.pedidos360.audit;
import cl.duoc.pedidos360.common.*;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.springframework.web.bind.annotation.*;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.context.annotation.Import;
import org.slf4j.*;
import java.time.Instant;
import java.util.*;
@RestController @RequestMapping("/api/audit") @Import(StreamingConfig.class)
public class AuditController {
    private static final Logger log=LoggerFactory.getLogger(AuditController.class);private final Store store;
    public AuditController(Store store) { this.store=store; }
    @KafkaListener(topics="audit.timeline",groupId="pedidos360-audit") @Transactional
    public void consume(String raw) { var event=store.parse(raw);if(!event.hasNonNull("eventId")||!event.hasNonNull("timestamp")||!event.path("order").has("id")) throw new IllegalArgumentException("Envelope incompleto");Instant.parse(event.path("timestamp").asText());String key="audit:"+event.path("eventId").asText();if(!store.exists(key)) store.insert(key,"audit",event); }
    @KafkaListener(topics="audit.timeline.ms-pedidos360-audit.DLT",groupId="pedidos360-audit-dlq-log")
    public void deadLetter(org.apache.kafka.clients.consumer.ConsumerRecord<String,String> record) { log.error("Kafka DLT topic={} partition={} offset={} headers={}",record.topic(),record.partition(),record.offset(),record.headers()); }
    @GetMapping({"","/events"}) public List<ObjectNode> events(@RequestParam(required=false) String user,@RequestParam(required=false) String type,@RequestParam(required=false) Long orderId,@RequestParam(required=false) Instant from,@RequestParam(required=false) Instant to) {
        return store.all("audit").stream().filter(e -> user==null||e.path("actor").asText().equals(user)||e.path("order").path("cliente").asText().equals(user)).filter(e -> type==null||e.path("type").asText().equals(type)).filter(e -> orderId==null||e.path("order").path("id").asLong()==orderId).filter(e -> from==null||!Instant.parse(e.path("timestamp").asText()).isBefore(from)).filter(e -> to==null||!Instant.parse(e.path("timestamp").asText()).isAfter(to)).sorted(Comparator.comparing(e -> e.path("timestamp").asText())).toList();
    }
}
