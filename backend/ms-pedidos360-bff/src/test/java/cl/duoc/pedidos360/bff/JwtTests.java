package cl.duoc.pedidos360.bff;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.web.servlet.MockMvc;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
import com.nimbusds.jose.*;
import com.nimbusds.jose.crypto.RSASSASigner;
import com.nimbusds.jose.jwk.*;
import com.nimbusds.jose.jwk.gen.RSAKeyGenerator;
import com.nimbusds.jwt.*;
import com.sun.net.httpserver.HttpServer;
import java.net.InetSocketAddress;
import java.time.Instant;
import java.util.*;
@SpringBootTest(properties={"ENTRA_ISSUER_URI=https://issuer.example/v2.0","ENTRA_AUDIENCE=api-test","RABBIT_PASSWORD=test","spring.rabbitmq.dynamic=false"}) @AutoConfigureMockMvc
class JwtTests {
    static HttpServer server;static RSAKey key;
    static { try { key=new RSAKeyGenerator(2048).keyID("test").generate();server=HttpServer.create(new InetSocketAddress("127.0.0.1",0),0);server.createContext("/jwks",e->{var body=new JWKSet(key.toPublicJWK()).toString().getBytes();e.getResponseHeaders().add("Content-Type","application/json");e.sendResponseHeaders(200,body.length);e.getResponseBody().write(body);e.close();});server.createContext("/api/orders",e->{if(!e.getRequestHeaders().getFirst("Authorization").startsWith("Bearer ")) { e.sendResponseHeaders(401,-1); }else {var body="[]".getBytes();e.getResponseHeaders().add("Content-Type","application/json");e.sendResponseHeaders(200,body.length);e.getResponseBody().write(body);}e.close();});server.start(); }catch(Exception e) { throw new RuntimeException(e); } }
    @DynamicPropertySource static void props(DynamicPropertyRegistry r) { r.add("spring.security.oauth2.resourceserver.jwt.jwk-set-uri",()->"http://127.0.0.1:"+server.getAddress().getPort()+"/jwks");r.add("app.orders-url",()->"http://127.0.0.1:"+server.getAddress().getPort()); }
    @Autowired MockMvc mvc;
    @AfterAll static void stop() { server.stop(0); }
    String token(String issuer,String audience,long seconds,String role,RSAKey signingKey) throws Exception { var claims=new JWTClaimsSet.Builder().subject("alice").issuer(issuer).audience(audience).issueTime(Date.from(Instant.now().minusSeconds(120))).expirationTime(Date.from(Instant.now().plusSeconds(seconds))).claim("scp","access_as_user").claim("roles",List.of(role)).build();var jwt=new SignedJWT(new JWSHeader.Builder(JWSAlgorithm.RS256).keyID("test").build(),claims);jwt.sign(new RSASSASigner(signingKey));return jwt.serialize(); }
    @Test void validatesRealSignatureIssuerAudienceAndExpiry() throws Exception { String good=token("https://issuer.example/v2.0","api-test",3600,"Customer",key);mvc.perform(get("/api/me").header("Authorization","Bearer "+good)).andExpect(status().isOk()).andExpect(jsonPath("$.roles[0]").value("Customer"));
        for(String bad:List.of(token("https://other.example","api-test",3600,"Admin",key),token("https://issuer.example/v2.0","wrong-audience",3600,"Admin",key),token("https://issuer.example/v2.0","api-test",-120,"Admin",key),token("https://issuer.example/v2.0","api-test",3600,"Admin",new RSAKeyGenerator(2048).generate()))) mvc.perform(get("/api/me").header("Authorization","Bearer "+bad)).andExpect(status().isUnauthorized()); }
    @Test void forwardsAccessTokenAndKeepsRoleRestrictions() throws Exception { var customer=token("https://issuer.example/v2.0","api-test",3600,"Customer",key);mvc.perform(get("/api/orders").header("Authorization","Bearer "+customer)).andExpect(status().isOk()).andExpect(content().json("[]"));mvc.perform(get("/api/report/kpis").header("Authorization","Bearer "+customer)).andExpect(status().isForbidden()); }
}
