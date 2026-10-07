package cl.duoc.pedidos360.notify;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.amqp.core.*;
import org.springframework.mail.javamail.JavaMailSender;
import com.rabbitmq.client.Channel;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.file.*;
import static org.mockito.Mockito.*;
import static org.assertj.core.api.Assertions.*;
class NotificationTests {
    @TempDir Path dir;
    Message command(String queue,String body) { var p=new MessageProperties();p.setConsumerQueue(queue);p.setDeliveryTag(10);return new Message(body.getBytes(),p); }
    @Test void duplicateCommandDoesNotSendEmailTwiceAndAcknowledgesBoth() throws Exception { var mail=mock(JavaMailSender.class);var channel=mock(Channel.class);var consumer=new Notifications(new ObjectMapper(),mail,dir.toString(),"sender@example.com");var m=command("q.cmd.email","{\"eventId\":\"aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa\",\"type\":\"EmailRequested\",\"order\":{\"id\":1,\"email\":\"a@example.com\",\"estado\":\"CREADO\"}}");consumer.consume(m,channel);consumer.consume(m,channel);verify(mail,times(1)).send(any(org.springframework.mail.SimpleMailMessage.class));verify(channel,times(2)).basicAck(10,false); }
    @Test void invalidMessageIsNackedWithoutRequeueForDeadLetterRouting() throws Exception { var channel=mock(Channel.class);var consumer=new Notifications(new ObjectMapper(),mock(JavaMailSender.class),dir.toString(),"sender@example.com");consumer.consume(command("q.cmd.email","{}"),channel);verify(channel).basicNack(10,false,false); }
    @Test void invoiceCommandProducesReadablePdf() throws Exception { var channel=mock(Channel.class);var consumer=new Notifications(new ObjectMapper(),mock(JavaMailSender.class),dir.toString(),"sender@example.com");consumer.consume(command("q.cmd.invoice","{\"eventId\":\"bbbbbbbb-bbbb-bbbb-bbbb-bbbbbbbbbbbb\",\"type\":\"InvoiceRequested\",\"order\":{\"id\":1,\"total\":2000,\"estado\":\"ENTREGADO\",\"items\":[]}}"),channel);try(var pdf=org.apache.pdfbox.Loader.loadPDF(dir.resolve("invoice-bbbbbbbb-bbbb-bbbb-bbbb-bbbbbbbbbbbb.pdf").toFile())) { assertThat(new org.apache.pdfbox.text.PDFTextStripper().getText(pdf)).contains("Total CLP: 2000"); }verify(channel).basicAck(10,false); }
}
