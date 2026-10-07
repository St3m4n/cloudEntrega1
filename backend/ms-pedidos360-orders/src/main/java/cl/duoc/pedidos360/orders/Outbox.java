package cl.duoc.pedidos360.orders;
import cl.duoc.pedidos360.common.*;
import org.springframework.stereotype.Component;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.amqp.rabbit.connection.CorrelationData;
import org.springframework.amqp.core.*;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.transaction.PlatformTransactionManager;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.TimeUnit;
import org.slf4j.*;
@Component
public class Outbox {
    private static final Logger log=LoggerFactory.getLogger(Outbox.class);
    private final Store store;private final KafkaTemplate<String,String> kafka;private final RabbitTemplate rabbit;private final TransactionTemplate tx;
    public Outbox(Store s,KafkaTemplate<String,String> k,RabbitTemplate r,PlatformTransactionManager manager) { store=s;kafka=k;rabbit=r;tx=new TransactionTemplate(manager); }
    @Scheduled(fixedDelayString="${OUTBOX_DELAY_MS:2000}") public void publish() {
        for(var candidate:store.all("outbox").stream().filter(e->!e.has("nextAttemptAt")||java.time.Instant.parse(e.path("nextAttemptAt").asText()).isBefore(java.time.Instant.now())).sorted(java.util.Comparator.comparing(e->e.path("createdAt").asText())).limit(50).toList()) tx.executeWithoutResult(status -> {
            String key="outbox:"+candidate.path("id").asText();if(!store.exists(key)) return;var entry=store.lock(key);
            try {
                var event=entry.path("message");String destination=entry.path("destination").asText();
                if(destination.equals("orders.events")||destination.equals("audit.timeline")) kafka.send(destination,event.path("correlationId").asText(),event.toString()).get(20,TimeUnit.SECONDS);
                else {
                    var properties=new MessageProperties();properties.setContentType("application/json");properties.setDeliveryMode(MessageDeliveryMode.PERSISTENT);properties.setMessageId(event.path("eventId").asText());
                    var correlation=new CorrelationData(event.path("eventId").asText());rabbit.send("cmd.direct",destination,new Message(event.toString().getBytes(StandardCharsets.UTF_8),properties),correlation);
                    if(!correlation.getFuture().get(10,TimeUnit.SECONDS).isAck()||correlation.getReturned()!=null) throw new IllegalStateException("RabbitMQ no confirmó el enrutamiento");
                }
                store.remove(key);
            }catch(Exception e) { int attempts=entry.path("attempts").asInt()+1;entry.put("attempts",attempts).put("nextAttemptAt",java.time.Instant.now().plusSeconds(Math.min(300,1L<<Math.min(attempts,8))).toString());store.put(key,"outbox",entry);log.warn("Outbox pendiente id={} destino={} intento={} error={}",entry.path("id"),entry.path("destination"),entry.path("attempts"),e.toString()); }
        });
    }
}
