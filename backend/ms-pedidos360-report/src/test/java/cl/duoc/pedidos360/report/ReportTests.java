package cl.duoc.pedidos360.report;
import cl.duoc.pedidos360.common.*;
import cl.duoc.pedidos360.common.kafka.OrderEvent;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import java.time.*;
import static org.assertj.core.api.Assertions.*;
@SpringBootTest(properties={"spring.datasource.url=jdbc:h2:mem:report;MODE=Oracle;DB_CLOSE_DELAY=-1","ENTRA_ISSUER_URI=https://example.invalid/v2.0","ENTRA_AUDIENCE=test","RABBIT_PASSWORD=test","spring.rabbitmq.dynamic=false","spring.kafka.listener.auto-startup=false"})
class ReportTests {
    @Autowired ReportController controller;@Autowired ProjectionService projections;@Autowired ObjectMapper mapper;@Autowired Documents documents;
    @Autowired jakarta.validation.Validator validator;@MockitoBean JwtDecoder decoder;
    @BeforeEach void clean() { documents.deleteAll(); }
    OrderEvent event(int version,String state) throws Exception {
        String json="{\"type\":\"OrderUpdated\",\"eventId\":\"e"+version+"\",\"timestamp\":\""+Instant.now()+"\",\"correlationId\":\"1\",\"order\":{\"id\":1,\"total\":2000,\"items\":[{\"productId\":1,\"nombre\":\"Cafe\",\"quantity\":2,\"precio\":1000}],\"version\":"+version+",\"estado\":\""+state+"\",\"creadoEn\":\""+Instant.now().minusSeconds(3600)+"\",\"entregadoEn\":\""+Instant.now()+"\"}}";
        return mapper.readValue(json,OrderEvent.class);
    }
    @Test void duplicatedAndOlderEventsDoNotRegressProjectionOrDoubleCountSales() throws Exception {
        assertThat(projections.apply(event(6,"ENTREGADO"))).isTrue();assertThat(projections.apply(event(6,"ENTREGADO"))).isFalse();assertThat(projections.apply(event(2,"ACEPTADO"))).isFalse();
        var k=controller.kpis("last24h");assertThat(k.get("sales").toString()).isEqualTo("2000");assertThat(k.get("deliveredOrders")).isEqualTo(1);assertThat(((Number)k.get("averageLeadTimeMinutes")).doubleValue()).isCloseTo(60,org.assertj.core.data.Offset.offset(0.1));assertThat(controller.top("last7d").getFirst().get("quantity")).isEqualTo(2L);
    }
    @Test void activeOrdersAreNotSales() throws Exception { projections.apply(event(1,"CREADO"));var k=controller.kpis("last24h");assertThat(k.get("sales").toString()).isEqualTo("0");assertThat(k.get("activeOrders")).isEqualTo(1L); }
    @Test void malformedEventsFailValidationForDltAndUnsupportedRangesAreRejected() throws Exception {
        assertThat(validator.validate(mapper.readValue("{}",OrderEvent.class))).isNotEmpty();
        assertThatThrownBy(()->controller.kpis("unknown")).isInstanceOf(org.springframework.web.server.ResponseStatusException.class);
    }
}
