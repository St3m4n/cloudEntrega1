package cl.duoc.pedidos360.audit;
import cl.duoc.pedidos360.common.*;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
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
    @Autowired AuditController controller;@Autowired Documents documents;@Autowired MockMvc mvc;@MockitoBean JwtDecoder decoder;
    @BeforeEach void clean() { documents.deleteAll(); }
    @Test void deduplicatesAndFiltersByOrderActorAndType() { String event="{\"eventId\":\"e1\",\"timestamp\":\"2026-10-07T12:00:00Z\",\"type\":\"OrderCreated\",\"actor\":\"alice\",\"order\":{\"id\":1}}";controller.consume(event);controller.consume(event);assertThat(controller.events("alice","OrderCreated",1L,null,null)).hasSize(1);assertThat(controller.events("bob",null,null,null,null)).isEmpty(); }
    @Test void auditIsReadOnlyAndOperatorCannotReadIt() throws Exception { mvc.perform(get("/api/audit/events").with(jwt().authorities(new SimpleGrantedAuthority("ROLE_Operator")))).andExpect(status().isForbidden());mvc.perform(get("/api/audit/events").with(jwt().authorities(new SimpleGrantedAuthority("ROLE_Auditor")))).andExpect(status().isOk());mvc.perform(post("/api/audit/events").with(jwt().authorities(new SimpleGrantedAuthority("ROLE_Admin")))).andExpect(status().isForbidden()); }
}
