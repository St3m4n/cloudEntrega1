package cl.duoc.pedidos360.catalog;
import cl.duoc.pedidos360.common.*;
import com.fasterxml.jackson.databind.ObjectMapper;
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
@SpringBootTest(properties={"spring.datasource.url=jdbc:h2:mem:catalog;MODE=Oracle;DB_CLOSE_DELAY=-1","ENTRA_ISSUER_URI=https://example.invalid/v2.0","ENTRA_AUDIENCE=test","RABBIT_PASSWORD=test","spring.rabbitmq.dynamic=false"})
@AutoConfigureMockMvc
class CatalogTests {
    @Autowired MockMvc mvc;@Autowired ObjectMapper mapper;@Autowired Documents documents;@MockitoBean JwtDecoder decoder;
    @BeforeEach void clean() { documents.deleteAll(); }
    long product(int stock) throws Exception { return mapper.readTree(mvc.perform(post("/api/catalog/products").with(jwt().authorities(new SimpleGrantedAuthority("ROLE_Admin"))).contentType("application/json").content("{\"nombre\":\"Cafe\",\"categoria\":\"Bebidas\",\"precio\":1000,\"stock\":"+stock+"}")).andExpect(status().isCreated()).andReturn().getResponse().getContentAsString()).path("id").asLong(); }
    @Test void jwtAndRoleAreRequired() throws Exception { mvc.perform(get("/api/catalog/products")).andExpect(status().isUnauthorized());mvc.perform(post("/api/catalog/products").with(jwt().authorities(new SimpleGrantedAuthority("ROLE_Customer"))).contentType("application/json").content("{}")).andExpect(status().isForbidden()); }
    @Test void reservationAndReleaseAreIdempotent() throws Exception {
        long id=product(5);String input="{\"items\":[{\"productId\":"+id+",\"quantity\":3}]}";
        for(int i=0;i<2;i++) mvc.perform(post("/api/catalog/reservations/100").with(jwt().authorities(new SimpleGrantedAuthority("ROLE_Operator"))).contentType("application/json").content(input)).andExpect(status().isOk());
        mvc.perform(get("/api/catalog/products/"+id).with(jwt().authorities(new SimpleGrantedAuthority("ROLE_Customer")))).andExpect(jsonPath("$.stock").value(2));
        for(int i=0;i<2;i++) mvc.perform(delete("/api/catalog/reservations/100").with(jwt().authorities(new SimpleGrantedAuthority("ROLE_Operator")))).andExpect(status().isNoContent());
        mvc.perform(get("/api/catalog/products/"+id).with(jwt().authorities(new SimpleGrantedAuthority("ROLE_Customer")))).andExpect(jsonPath("$.stock").value(5));
    }
    @Test void insufficientStockRollsBackWholeReservation() throws Exception {
        long first=product(10),second=product(1);String input="{\"items\":[{\"productId\":"+first+",\"quantity\":2},{\"productId\":"+second+",\"quantity\":2}]}";
        mvc.perform(post("/api/catalog/reservations/200").with(jwt().authorities(new SimpleGrantedAuthority("ROLE_Operator"))).contentType("application/json").content(input)).andExpect(status().isConflict());
        mvc.perform(get("/api/catalog/products/"+first).with(jwt().authorities(new SimpleGrantedAuthority("ROLE_Admin")))).andExpect(jsonPath("$.stock").value(10));
        assertThat(documents.existsById("reservation:200")).isFalse();
    }
    @Test void invalidProductIsRejected() throws Exception { mvc.perform(post("/api/catalog/products").with(jwt().authorities(new SimpleGrantedAuthority("ROLE_Admin"))).contentType("application/json").content("{\"nombre\":\"Cafe\",\"categoria\":\"B\",\"precio\":-1,\"stock\":-1}")).andExpect(status().isBadRequest()); }
    @Test void concurrentOrdersCannotOversell() throws Exception {
        long id=product(5);String input="{\"items\":[{\"productId\":"+id+",\"quantity\":3}]}";
        var tasks=java.util.stream.LongStream.of(301,302).mapToObj(orderId->java.util.concurrent.CompletableFuture.supplyAsync(()-> { try { return mvc.perform(post("/api/catalog/reservations/"+orderId).with(jwt().authorities(new SimpleGrantedAuthority("ROLE_Operator"))).contentType("application/json").content(input)).andReturn().getResponse().getStatus(); }catch(Exception e) { throw new RuntimeException(e); } })).toList();
        assertThat(tasks.stream().map(java.util.concurrent.CompletableFuture::join).toList()).containsExactlyInAnyOrder(200,409);
        mvc.perform(get("/api/catalog/products/"+id).with(jwt().authorities(new SimpleGrantedAuthority("ROLE_Admin")))).andExpect(jsonPath("$.stock").value(2));
    }
}
