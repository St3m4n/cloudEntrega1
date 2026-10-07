package cl.duoc.pedidos360.notify;
import cl.duoc.pedidos360.common.RabbitTopology;
import com.fasterxml.jackson.databind.*;
import org.springframework.stereotype.Component;
import org.springframework.context.annotation.Import;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.amqp.core.Message;
import com.rabbitmq.client.Channel;
import org.springframework.mail.*;
import org.springframework.mail.javamail.JavaMailSender;
import org.slf4j.*;
import java.nio.file.*;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
@Component @Import(RabbitTopology.class)
public class Notifications {
    private static final Logger log=LoggerFactory.getLogger(Notifications.class);
    private final ObjectMapper mapper;private final JavaMailSender mail;private final Path directory;private final String sender;
    public Notifications(ObjectMapper mapper,JavaMailSender mail,@Value("${app.notify-dir}") String directory,@Value("${app.mail-from}") String sender) throws IOException { this.mapper=mapper;this.mail=mail;this.directory=Path.of(directory);this.sender=sender;Files.createDirectories(this.directory); }
    @RabbitListener(queues={"q.cmd.email","q.cmd.kitchen","q.cmd.invoice"})
    public synchronized void consume(Message message,Channel channel) throws IOException {
        long tag=message.getMessageProperties().getDeliveryTag();
        try {
            var event=mapper.readTree(message.getBody());String id=event.path("eventId").asText();
            if(!id.matches("[a-fA-F0-9-]{36}")||!event.hasNonNull("type")||!event.path("order").has("id")) throw new IllegalArgumentException("Envelope inválido");
            Path receipt=directory.resolve(id+".done");
            if(!Files.exists(receipt)) {
                var order=event.path("order");String queue=message.getMessageProperties().getConsumerQueue();
                switch(queue) {
                    case "q.cmd.email" -> { var email=new SimpleMailMessage();email.setFrom(sender);email.setTo(order.path("email").asText());email.setSubject("Pedido "+order.path("id").asText()+": "+order.path("estado").asText());email.setText("El estado de su pedido es "+order.path("estado").asText()+". Total: "+order.path("total").asText());mail.send(email); }
                    case "q.cmd.kitchen" -> atomic(directory.resolve("ticket-"+id+".json"),event.toPrettyString().getBytes(StandardCharsets.UTF_8));
                    case "q.cmd.invoice" -> invoice(directory.resolve("invoice-"+id+".pdf"),order);
                    default -> throw new IllegalArgumentException("Cola desconocida");
                }
                atomic(receipt,"processed".getBytes(StandardCharsets.UTF_8));log.info("Comando completado eventId={} queue={}",id,queue);
            }
            channel.basicAck(tag,false);
        }catch(Exception e) { log.error("Comando fallido messageId={} cola={} error={}",message.getMessageProperties().getMessageId(),message.getMessageProperties().getConsumerQueue(),e.toString());channel.basicNack(tag,false,false); }
    }
    @RabbitListener(queues={"q.cmd.email.dlq","q.cmd.kitchen.dlq","q.cmd.invoice.dlq"},autoStartup="${DLQ_LOGGER_ENABLED:true}")
    public synchronized void deadLetter(Message message,Channel channel) throws IOException {
        try { String id=java.util.UUID.randomUUID().toString();atomic(directory.resolve("dlq-"+id+".json"),mapper.writeValueAsBytes(java.util.Map.of("queue",message.getMessageProperties().getConsumerQueue(),"headers",message.getMessageProperties().getHeaders(),"body",new String(message.getBody(),StandardCharsets.UTF_8))));log.error("RabbitMQ DLQ archivada id={} queue={}",id,message.getMessageProperties().getConsumerQueue());channel.basicAck(message.getMessageProperties().getDeliveryTag(),false); }
        catch(Exception e) { channel.basicNack(message.getMessageProperties().getDeliveryTag(),false,true); }
    }
    private static void atomic(Path path,byte[] bytes) throws IOException { var temp=Files.createTempFile(path.getParent(),"p360-",".tmp");try { Files.write(temp,bytes);Files.move(temp,path,StandardCopyOption.ATOMIC_MOVE,StandardCopyOption.REPLACE_EXISTING); } finally { Files.deleteIfExists(temp); } }
    private static void invoice(Path path,JsonNode order) throws IOException {
        try(var document=new org.apache.pdfbox.pdmodel.PDDocument()) {
            var lines=new java.util.ArrayList<String>();lines.add("Pedidos360 - Comprobante interno (sin validez tributaria)");lines.add("Pedido: "+order.path("id").asText());lines.add("Total CLP: "+order.path("total").asText());lines.add("Estado: "+order.path("estado").asText());
            for(var item:order.path("items")) lines.add("Producto "+item.path("productId").asText()+" x "+item.path("quantity").asText()+" @ "+item.path("precio").asText());
            for(int start=0;start<lines.size();start+=36) {
                var page=new org.apache.pdfbox.pdmodel.PDPage();document.addPage(page);
                try(var content=new org.apache.pdfbox.pdmodel.PDPageContentStream(document,page)) {
                    content.beginText();content.setFont(new org.apache.pdfbox.pdmodel.font.PDType1Font(org.apache.pdfbox.pdmodel.font.Standard14Fonts.FontName.HELVETICA),12);content.newLineAtOffset(50,750);
                    for(String line:lines.subList(start,Math.min(start+36,lines.size()))) { content.showText(line);content.newLineAtOffset(0,-18); }
                    content.endText();
                }
            }
            var bytes=new java.io.ByteArrayOutputStream();document.save(bytes);atomic(path,bytes.toByteArray());
        }
    }
}
