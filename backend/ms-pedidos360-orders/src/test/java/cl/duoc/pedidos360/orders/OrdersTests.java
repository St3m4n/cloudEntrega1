package cl.duoc.pedidos360.orders;
import cl.duoc.pedidos360.common.*;
import com.fasterxml.jackson.databind.*;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.DynamicPropertyRegistry;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
import static org.assertj.core.api.Assertions.*;
import com.sun.net.httpserver.HttpServer;
import java.net.InetSocketAddress;
import java.util.concurrent.atomic.AtomicInteger;
@SpringBootTest(properties={"spring.datasource.url=jdbc:h2:mem:orders;MODE=Oracle;DB_CLOSE_DELAY=-1","ENTRA_ISSUER_URI=https://example.invalid/v2.0","ENTRA_AUDIENCE=test","RABBIT_PASSWORD=test","spring.rabbitmq.dynamic=false"})
@AutoConfigureMockMvc
class OrdersTests {
    static HttpServer server;static AtomicInteger reserveCalls=new AtomicInteger();static AtomicInteger reply=new AtomicInteger(200);
    static { try { server=HttpServer.create(new InetSocketAddress("127.0.0.1",0),0);server.createContext("/",e->{var body=(e.getRequestURI().getPath().contains("products")?"{\"id\":1,\"nombre\":\"Cafe\",\"precio\":1000,\"stock\":10}":"{}").getBytes();if(e.getRequestURI().getPath().contains("reservations")) reserveCalls.incrementAndGet();e.getRequestBody().readAllBytes();e.getResponseHeaders().add("Content-Type","application/json");e.sendResponseHeaders(e.getRequestURI().getPath().contains("reservations")?reply.get():200,body.length);e.getResponseBody().write(body);e.close();});server.start(); } catch(Exception ex) { throw new RuntimeException(ex); } }
    @DynamicPropertySource static void props(DynamicPropertyRegistry registry) { registry.add("app.catalog-url",()->"http://127.0.0.1:"+server.getAddress().getPort()); }
    @Autowired MockMvc mvc;@Autowired ObjectMapper mapper;@Autowired Documents documents;@Autowired Store store;
    @MockitoBean JwtDecoder decoder;@MockitoBean Outbox outbox;
    @MockitoBean org.springframework.kafka.core.KafkaTemplate<String,String> kafka;
    @MockitoBean org.springframework.amqp.rabbit.core.RabbitTemplate rabbit;
    @Autowired org.springframework.transaction.PlatformTransactionManager transactionManager;
    @BeforeEach void clean() { documents.deleteAll();reserveCalls.set(0);reply.set(200); }
    @AfterAll static void stop() { server.stop(0); }
    long create() throws Exception { return mapper.readTree(mvc.perform(post("/api/orders").with(jwt().jwt(j->j.subject("alice")).authorities(new SimpleGrantedAuthority("ROLE_Customer"))).contentType("application/json").content("{\"email\":\"alice@example.com\",\"items\":[{\"productId\":1,\"quantity\":2}]}" )).andExpect(status().isCreated()).andExpect(jsonPath("$.total").value(2000)).andReturn().getResponse().getContentAsString()).path("id").asLong(); }
    void change(long id,String state,int expected) throws Exception { mvc.perform(put("/api/orders/"+id+"/status").with(jwt().authorities(new SimpleGrantedAuthority("ROLE_Operator"))).contentType("application/json").content("{\"status\":\""+state+"\"}")).andExpect(status().is(expected)); }
    @Test void cannotSkipAcceptanceAndRepeatedAcceptanceDoesNotReserveTwice() throws Exception { long id=create();change(id,"DESPACHADO",409);change(id,"ACEPTADO",200);change(id,"ACEPTADO",200);assertThat(reserveCalls.get()).isEqualTo(1);change(id,"EN_PREPARACION",200);change(id,"DESPACHADO",200);change(id,"ENTREGADO",200);change(id,"CANCELADO",409);assertThat(store.all("outbox")).hasSize(17); }
    @Test void customerCannotReadOtherCustomerOrdersOrChangeStatus() throws Exception { long id=create();mvc.perform(get("/api/orders/"+id).with(jwt().jwt(j->j.subject("bob")).authorities(new SimpleGrantedAuthority("ROLE_Customer")))).andExpect(status().isNotFound());mvc.perform(get("/api/orders").with(jwt().jwt(j->j.subject("bob")).authorities(new SimpleGrantedAuthority("ROLE_Customer")))).andExpect(content().json("[]"));mvc.perform(put("/api/orders/"+id+"/status").with(jwt().authorities(new SimpleGrantedAuthority("ROLE_Customer"))).contentType("application/json").content("{\"status\":\"ACEPTADO\"}")).andExpect(status().isForbidden()); }
    @Test void failedStockDoesNotAdvanceStateOrPublishEvents() throws Exception { long id=create();reply.set(409);change(id,"ACEPTADO",409);assertThat(store.get("order:"+id).path("estado").asText()).isEqualTo("CREADO");assertThat(store.all("outbox")).hasSize(3);assertThat(store.exists("stock-intent:"+id)).isFalse(); }
    @Test void ambiguousStockFailureRequiresSameOperationRetry() throws Exception { long id=create();reply.set(503);change(id,"ACEPTADO",503);change(id,"CANCELADO",409);assertThat(store.exists("stock-intent:"+id)).isTrue();reply.set(200);change(id,"ACEPTADO",200);assertThat(store.exists("stock-intent:"+id)).isFalse(); }
    @Test void failedBrokerKeepsOutboxForLaterRetry() {
        store.insert("outbox:test","outbox",store.parse("{\"id\":\"test\",\"destination\":\"orders.events\",\"createdAt\":\"2026-10-07T00:00:00Z\",\"message\":{\"correlationId\":\"1\"}}"));
        org.mockito.Mockito.when(kafka.send(org.mockito.ArgumentMatchers.anyString(),org.mockito.ArgumentMatchers.anyString(),org.mockito.ArgumentMatchers.anyString())).thenReturn(java.util.concurrent.CompletableFuture.failedFuture(new RuntimeException("broker offline")));
        new Outbox(store,kafka,rabbit,transactionManager).publish();var entry=store.get("outbox:test");assertThat(entry.path("attempts").asInt()).isEqualTo(1);assertThat(entry.has("nextAttemptAt")).isTrue();
        new Outbox(store,kafka,rabbit,transactionManager).publish();org.mockito.Mockito.verify(kafka,org.mockito.Mockito.times(1)).send(org.mockito.ArgumentMatchers.anyString(),org.mockito.ArgumentMatchers.anyString(),org.mockito.ArgumentMatchers.anyString());
    }
    @Test void rabbitOutboxIsRemovedOnlyAfterPublisherConfirmation() {
        store.insert("outbox:test","outbox",store.parse("{\"id\":\"test\",\"destination\":\"email.send\",\"createdAt\":\"2026-10-07T00:00:00Z\",\"message\":{\"eventId\":\"event-1\",\"correlationId\":\"1\"}}"));
        org.mockito.Mockito.doAnswer(call->{var correlation=(org.springframework.amqp.rabbit.connection.CorrelationData)call.getArgument(3);correlation.getFuture().complete(new org.springframework.amqp.rabbit.connection.CorrelationData.Confirm(true,null));return null;}).when(rabbit).send(org.mockito.ArgumentMatchers.eq("cmd.direct"),org.mockito.ArgumentMatchers.eq("email.send"),org.mockito.ArgumentMatchers.any(org.springframework.amqp.core.Message.class),org.mockito.ArgumentMatchers.any(org.springframework.amqp.rabbit.connection.CorrelationData.class));
        new Outbox(store,kafka,rabbit,transactionManager).publish();assertThat(store.exists("outbox:test")).isFalse();
    }
}
