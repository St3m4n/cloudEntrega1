package cl.duoc.pedidos360.report;
import cl.duoc.pedidos360.common.*;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import java.time.*;
import static org.assertj.core.api.Assertions.*;
@SpringBootTest(properties={"spring.datasource.url=jdbc:h2:mem:report;MODE=Oracle;DB_CLOSE_DELAY=-1","ENTRA_ISSUER_URI=https://example.invalid/v2.0","ENTRA_AUDIENCE=test","RABBIT_PASSWORD=test","spring.rabbitmq.dynamic=false","spring.kafka.listener.auto-startup=false"})
class ReportTests {
    @Autowired ReportController controller;@Autowired Store store;@Autowired Documents documents;@MockitoBean JwtDecoder decoder;
    @BeforeEach void clean() { documents.deleteAll(); }
    String event(int version,String state) { var e=store.parse("{\"eventId\":\"e1\",\"order\":{\"id\":1,\"total\":2000,\"items\":[{\"productId\":1,\"nombre\":\"Cafe\",\"quantity\":2}],\"version\":"+version+",\"estado\":\""+state+"\"}}");var o=(com.fasterxml.jackson.databind.node.ObjectNode)e.path("order");o.put("creadoEn",Instant.now().minusSeconds(3600).toString());o.put("entregadoEn",Instant.now().toString());return e.toString(); }
    @Test void duplicatedAndOlderEventsDoNotRegressProjectionOrDoubleCountSales() { controller.consume(event(6,"ENTREGADO"));controller.consume(event(6,"ENTREGADO"));controller.consume(event(2,"ACEPTADO"));var k=controller.kpis("last24h");assertThat(k.get("sales").toString()).isEqualTo("2000");assertThat(k.get("deliveredOrders")).isEqualTo(1);assertThat(((Number)k.get("averageLeadTimeMinutes")).doubleValue()).isCloseTo(60,org.assertj.core.data.Offset.offset(0.1));assertThat(controller.top("last7d").getFirst().get("quantity")).isEqualTo(2L); }
    @Test void activeOrdersAreNotSales() { controller.consume(event(1,"CREADO"));var k=controller.kpis("last24h");assertThat(k.get("sales").toString()).isEqualTo("0");assertThat(k.get("activeOrders")).isEqualTo(1L); }
    @Test void malformedEventsFailForDltAndUnsupportedRangesAreRejected() { assertThatThrownBy(()->controller.consume("{}" )).isInstanceOf(IllegalArgumentException.class);assertThatThrownBy(()->controller.kpis("unknown")).isInstanceOf(org.springframework.web.server.ResponseStatusException.class); }
}
