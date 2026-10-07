package cl.duoc.pedidos360.report;
import cl.duoc.pedidos360.common.*;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.springframework.web.bind.annotation.*;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.context.annotation.Import;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.http.HttpStatus;
import org.slf4j.*;
import java.time.*;
import java.time.temporal.ChronoUnit;
import java.math.BigDecimal;
import java.util.*;
@RestController @RequestMapping("/api/report") @Import(StreamingConfig.class)
public class ReportController {
    private static final Logger log=LoggerFactory.getLogger(ReportController.class);private final Store store;
    public ReportController(Store store) { this.store=store; }
    @KafkaListener(topics="orders.events",groupId="pedidos360-report") @Transactional
    public void consume(String raw) {
        var event=store.parse(raw);var order=event.path("order");if(!event.hasNonNull("eventId")||!order.has("id")||!order.has("version")||!order.has("estado")) throw new IllegalArgumentException("Evento inválido");
        String key="projection:"+order.path("id").asText();
        if(store.exists(key)) { var previous=store.lock(key);if(previous.path("version").asLong()>=order.path("version").asLong()) return;store.put(key,"projection",order); }
        else store.insert(key,"projection",order);
    }
    @KafkaListener(topics="orders.events.ms-pedidos360-report.DLT",groupId="pedidos360-report-dlq-log")
    public void deadLetter(org.apache.kafka.clients.consumer.ConsumerRecord<String,String> record) { log.error("Kafka DLT topic={} partition={} offset={} headers={}",record.topic(),record.partition(),record.offset(),record.headers()); }
    @GetMapping("/kpis") public Map<String,Object> kpis(@RequestParam(defaultValue="last24h") String range) {
        var since=since(range);var all=store.all("projection");var delivered=delivered(all,since);var sales=BigDecimal.ZERO;double lead=0;
        var hours=new TreeMap<String,BigDecimal>();var states=new TreeMap<String,Long>();var leads=new ArrayList<Map<String,Object>>();
        for(var o:all) states.merge(o.path("estado").asText(),1L,Long::sum);
        for(var o:delivered) { sales=sales.add(o.path("total").decimalValue());double minutes=Duration.between(Instant.parse(o.path("creadoEn").asText()),Instant.parse(o.path("entregadoEn").asText())).toMillis()/60000.0;lead+=minutes;leads.add(Map.of("id",o.path("id").asLong(),"minutes",minutes));String hour=Instant.parse(o.path("entregadoEn").asText()).truncatedTo(ChronoUnit.HOURS).toString();hours.merge(hour,o.path("total").decimalValue(),BigDecimal::add); }
        long active=all.stream().filter(o -> !Set.of("ENTREGADO","CANCELADO").contains(o.path("estado").asText())).count();
        return Map.of("sales",sales,"averageLeadTimeMinutes",delivered.isEmpty()?0:lead/delivered.size(),"activeOrders",active,"deliveredOrders",delivered.size(),"ordersCreated",all.stream().filter(o -> !Instant.parse(o.path("creadoEn").asText()).isBefore(since)).count(),"salesByHour",hours,"states",states,"leadTimes",leads,"range",range,"updatedAt",Instant.now().toString());
    }
    @GetMapping("/top-products") public List<Map<String,Object>> top(@RequestParam(defaultValue="last7d") String range) {
        var quantities=new HashMap<Long,Long>();var names=new HashMap<Long,String>();
        for(var o:delivered(store.all("projection"),since(range))) for(var i:o.path("items")) { long id=i.path("productId").asLong();quantities.merge(id,i.path("quantity").asLong(),Long::sum);names.put(id,i.path("nombre").asText()); }
        return quantities.entrySet().stream().sorted(Map.Entry.<Long,Long>comparingByValue().reversed()).limit(10).map(e -> Map.<String,Object>of("productId",e.getKey(),"nombre",names.get(e.getKey()),"quantity",e.getValue())).toList();
    }
    private static Instant since(String range) { return Instant.now().minus(switch(range) { case "last24h" -> Duration.ofHours(24);case "last7d" -> Duration.ofDays(7);default -> throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"Rango inválido"); }); }
    private static List<ObjectNode> delivered(List<ObjectNode> all,Instant since) { return all.stream().filter(o -> o.path("estado").asText().equals("ENTREGADO")).filter(o -> !Instant.parse(o.path("entregadoEn").asText()).isBefore(since)).toList(); }
}
