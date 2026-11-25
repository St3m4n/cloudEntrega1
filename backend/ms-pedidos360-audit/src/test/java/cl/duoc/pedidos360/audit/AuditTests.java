package cl.duoc.pedidos360.audit;
import cl.duoc.pedidos360.common.*;
import cl.duoc.pedidos360.common.kafka.OrderEvent;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.charset.StandardCharsets;
import org.apache.kafka.common.header.internals.RecordHeaders;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.kafka.listener.DefaultErrorHandler;
import org.springframework.kafka.support.serializer.ErrorHandlingDeserializer;
import org.springframework.kafka.support.serializer.JsonDeserializer;
import org.springframework.kafka.support.serializer.JsonSerializer;
import org.springframework.kafka.support.serializer.SerializationUtils;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.web.servlet.MockMvc;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
import static org.assertj.core.api.Assertions.*;
@SpringBootTest(properties={"spring.datasource.url=jdbc:h2:mem:audit;MODE=Oracle;DB_CLOSE_DELAY=-1","ENTRA_ISSUER_URI=https://example.invalid/v2.0","ENTRA_AUDIENCE=test","RABBIT_PASSWORD=test","spring.rabbitmq.dynamic=false","spring.kafka.listener.auto-startup=false"}) @AutoConfigureMockMvc
class AuditTests {
    static final String VALID="{\"eventId\":\"e1\",\"timestamp\":\"2026-10-07T12:00:00Z\",\"type\":\"OrderCreated\",\"correlationId\":\"1\",\"actor\":\"alice\",\"order\":{\"id\":1,\"estado\":\"CREADO\",\"version\":1,\"total\":2000}}";
    @Autowired AuditService audit;@Autowired AuditController controller;@Autowired Documents documents;@Autowired MockMvc mvc;@Autowired ObjectMapper mapper;
    @Autowired jakarta.validation.Validator validator;@Autowired DefaultErrorHandler errorHandler;@MockitoBean JwtDecoder decoder;
    @BeforeEach void clean() { documents.deleteAll(); }
    @Test void deduplicatesAndFiltersByOrderActorAndType() throws Exception {
        var event=mapper.readValue(VALID,OrderEvent.class);
        assertThat(audit.record(event)).isTrue();assertThat(audit.record(event)).isFalse();
        assertThat(controller.events("alice","OrderCreated",1L,null,null)).hasSize(1);assertThat(controller.events("bob",null,null,null,null)).isEmpty();
        assertThat(controller.events(null,null,null,null,null).getFirst().path("timestamp").asText()).isEqualTo("2026-10-07T12:00:00Z");
    }
    @Test void dtoValidationRejectsIncompleteEvents() throws Exception {
        var event=mapper.readValue("{\"eventId\":\"e2\",\"type\":\"OrderCreated\",\"correlationId\":\"1\",\"order\":{\"id\":-1}}",OrderEvent.class);
        assertThat(validator.validate(event)).extracting(v->v.getPropertyPath().toString()).contains("timestamp","order.id","order.estado","order.version");
        assertThat(validator.validate(mapper.readValue(VALID,OrderEvent.class))).isEmpty();
    }
    @Test void jsonSerializerAndDeserializerKeepTheContract() throws Exception {
        var event=mapper.readValue(VALID,OrderEvent.class);
        try(var serializer=new JsonSerializer<OrderEvent>();var deserializer=new ErrorHandlingDeserializer<>(new JsonDeserializer<>(OrderEvent.class,false))) {
            byte[] bytes=serializer.serialize("audit.timeline",event);
            assertThat(new String(bytes,StandardCharsets.UTF_8)).contains("\"timestamp\":\"2026-10-07T12:00:00Z\"").contains("\"order\":{\"id\":1");
            assertThat(deserializer.deserialize("audit.timeline",new RecordHeaders(),bytes)).isEqualTo(event);
            var headers=new RecordHeaders();
            assertThat(deserializer.deserialize("audit.timeline",headers,"{\"order\":{".getBytes(StandardCharsets.UTF_8))).isNull();
            assertThat(headers.lastHeader(SerializationUtils.VALUE_DESERIALIZER_EXCEPTION_HEADER)).isNotNull();
        }
    }
    @Test void validationAndFormatErrorsGoStraightToDlt() {
        assertThat(errorHandler.removeClassification(cl.duoc.pedidos360.common.kafka.InvalidEventException.class)).isFalse();
        assertThat(errorHandler.removeClassification(org.springframework.kafka.support.serializer.DeserializationException.class)).isFalse();
        errorHandler.addNotRetryableExceptions(cl.duoc.pedidos360.common.kafka.InvalidEventException.class,org.springframework.kafka.support.serializer.DeserializationException.class);
    }
    @Test void auditIsReadOnlyAndOperatorCannotReadIt() throws Exception { mvc.perform(get("/api/audit/events").with(jwt().authorities(new SimpleGrantedAuthority("ROLE_Operator")))).andExpect(status().isForbidden());mvc.perform(get("/api/audit/events").with(jwt().authorities(new SimpleGrantedAuthority("ROLE_Auditor")))).andExpect(status().isOk());mvc.perform(post("/api/audit/events").with(jwt().authorities(new SimpleGrantedAuthority("ROLE_Admin")))).andExpect(status().isForbidden()); }
}
