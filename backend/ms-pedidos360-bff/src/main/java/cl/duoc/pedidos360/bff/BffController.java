package cl.duoc.pedidos360.bff;
import org.springframework.web.bind.annotation.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.client.*;
import org.springframework.web.server.ResponseStatusException;
import jakarta.servlet.http.HttpServletRequest;
import java.net.URI;
import java.time.Duration;
import java.util.*;
@RestController
public class BffController {
    private final Map<String,String> urls;private final RestClient client;
    public BffController(@Value("${app.catalog-url}") String catalog,@Value("${app.orders-url}") String orders,@Value("${app.audit-url}") String audit,@Value("${app.report-url}") String report,@Value("${app.rabbit-admin-url}") String rabbit,@Value("${app.kafka-admin-url}") String kafka) {
        urls=Map.of("/api/catalog",catalog,"/api/orders",orders,"/api/audit",audit,"/api/report",report,"/api/admin/rabbit",rabbit,"/api/admin/kafka",kafka);
        var factory=new org.springframework.http.client.JdkClientHttpRequestFactory(java.net.http.HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(3)).build());factory.setReadTimeout(Duration.ofSeconds(15));client=RestClient.builder().requestFactory(factory).build();
    }
    @GetMapping("/api/me") public Map<String,Object> me(@AuthenticationPrincipal Jwt jwt) {
        var result=new LinkedHashMap<String,Object>();result.put("nombre",jwt.getClaimAsString("name"));result.put("usuario",jwt.getClaimAsString("preferred_username"));result.put("roles",Optional.ofNullable(jwt.getClaimAsStringList("roles")).orElse(List.of()));return result;
    }
    @GetMapping("/api/data") public Map<String,Object> data(@AuthenticationPrincipal Jwt jwt) { return Map.of("message","Pedidos360: sesión válida","subject",jwt.getSubject()); }
    @RequestMapping(value={"/api/catalog/**","/api/orders/**","/api/audit/**","/api/report/**","/api/admin/rabbit/**","/api/admin/kafka/**"},method={RequestMethod.GET,RequestMethod.POST,RequestMethod.PUT,RequestMethod.DELETE})
    public ResponseEntity<byte[]> proxy(HttpServletRequest request,@RequestBody(required=false) byte[] body) {
        String path=request.getRequestURI();if(path.startsWith("/api/catalog/reservations")) throw new ResponseStatusException(HttpStatus.NOT_FOUND);var base=urls.entrySet().stream().filter(e -> path.equals(e.getKey())||path.startsWith(e.getKey()+"/")).map(Map.Entry::getValue).findFirst().orElseThrow(()->new ResponseStatusException(HttpStatus.NOT_FOUND));
        try {
            var call=client.method(HttpMethod.valueOf(request.getMethod())).uri(URI.create(base+path+(request.getQueryString()==null?"":"?"+request.getQueryString()))).header(HttpHeaders.AUTHORIZATION,request.getHeader(HttpHeaders.AUTHORIZATION));
            if(request.getHeader("X-Correlation-Id")!=null) call.header("X-Correlation-Id",request.getHeader("X-Correlation-Id"));
            if(body!=null&&body.length>0) call.contentType(MediaType.APPLICATION_JSON).body(body);
            return call.exchange((req,res)-> { var headers=new HttpHeaders();if(res.getHeaders().getContentType()!=null) headers.setContentType(res.getHeaders().getContentType());return new ResponseEntity<>(res.getBody().readAllBytes(),headers,res.getStatusCode()); });
        }catch(RestClientException e) { throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE,"Microservicio no disponible",e); }
    }
}
