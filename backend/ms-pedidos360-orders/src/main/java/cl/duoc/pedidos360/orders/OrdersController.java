package cl.duoc.pedidos360.orders;
import cl.duoc.pedidos360.common.*;
import com.fasterxml.jackson.databind.node.*;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.client.*;
import org.springframework.web.server.ResponseStatusException;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.ThreadLocalRandom;

@RestController @RequestMapping("/api/orders") @Transactional
public class OrdersController {
    private final Store store;private final RestClient catalog;private final StockIntent intent;
    public OrdersController(Store store,StockIntent intent,@Value("${app.catalog-url}") String url) { this.store=store;this.intent=intent;var factory=new org.springframework.http.client.JdkClientHttpRequestFactory(java.net.http.HttpClient.newBuilder().connectTimeout(java.time.Duration.ofSeconds(3)).build());factory.setReadTimeout(java.time.Duration.ofSeconds(10));this.catalog=RestClient.builder().baseUrl(url).requestFactory(factory).build(); }
    public record Item(@Positive long productId,@Min(1) int quantity) {}
    public record Create(@NotEmpty @Size(max=100) List<@Valid Item> items,@NotBlank @Email @Size(max=254) String email) {}
    public record Change(@NotBlank String status) {}
    @PostMapping @ResponseStatus(HttpStatus.CREATED)
    public ObjectNode create(@Valid @RequestBody Create input,@AuthenticationPrincipal Jwt jwt,HttpServletRequest request) {
        long id=ThreadLocalRandom.current().nextLong(1,9_000_000_000_000_000L);var order=JsonNodeFactory.instance.objectNode();
        order.put("id",id).put("cliente",Optional.ofNullable(jwt.getClaimAsString("preferred_username")).orElse(jwt.getSubject())).put("owner",jwt.getSubject()).put("email",input.email()).put("estado","CREADO").put("creadoEn",Instant.now().toString()).put("version",1);
        var items=order.putArray("items");var total=BigDecimal.ZERO;var quantities=new TreeMap<Long,Integer>();
        for(var item:input.items()) quantities.merge(item.productId(),item.quantity(),Math::addExact);
        for(var entry:quantities.entrySet()) {
            ObjectNode product;
            try { product=catalog.get().uri("/api/catalog/products/{id}",entry.getKey()).header(HttpHeaders.AUTHORIZATION,"Bearer "+jwt.getTokenValue()).retrieve().body(ObjectNode.class); }
            catch(RestClientResponseException e) { throw new ResponseStatusException(HttpStatus.valueOf(e.getStatusCode().value()),"No se pudo consultar producto",e); }
            catch(RestClientException e) { throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE,"Catálogo no disponible",e); }
            if(product==null) throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE,"Catálogo sin respuesta");
            var price=product.path("precio").decimalValue();total=total.add(price.multiply(BigDecimal.valueOf(entry.getValue())));
            items.addObject().put("productId",entry.getKey()).put("nombre",product.path("nombre").asText()).put("quantity",entry.getValue()).put("precio",price);
        }
        order.put("total",total);store.insert("order:"+id,"order",order);enqueue(order,jwt,request);return order;
    }
    @GetMapping public List<ObjectNode> list(@AuthenticationPrincipal Jwt jwt,@RequestParam(required=false) String status,@RequestParam(required=false) Instant from,@RequestParam(required=false) Instant to) {
        if(status!=null&&!OrderFlow.STATES.contains(status)) throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"Estado inválido");
        return store.all("order").stream().filter(o -> staff(jwt)||o.path("owner").asText().equals(jwt.getSubject())).filter(o -> status==null||o.path("estado").asText().equals(status)).filter(o -> from==null||!Instant.parse(o.path("creadoEn").asText()).isBefore(from)).filter(o -> to==null||!Instant.parse(o.path("creadoEn").asText()).isAfter(to)).sorted(Comparator.comparing((ObjectNode o)->o.path("creadoEn").asText()).reversed()).toList();
    }
    @GetMapping("/{id}") public ObjectNode get(@PathVariable long id,@AuthenticationPrincipal Jwt jwt) { var o=store.get("order:"+id); if(!staff(jwt)&&!o.path("owner").asText().equals(jwt.getSubject())) throw new ResponseStatusException(HttpStatus.NOT_FOUND,"Pedido no encontrado");return o; }
    @PutMapping("/{id}/status") public ObjectNode change(@PathVariable long id,@Valid @RequestBody Change input,@AuthenticationPrincipal Jwt jwt,HttpServletRequest request) {
        var order=store.lock("order:"+id);String from=order.path("estado").asText(),to=input.status().replace("EN_PREPARACIÓN","EN_PREPARACION");intent.guard(id,to);OrderFlow.validate(from,to);if(from.equals(to)) return order;
        if(to.equals("ACEPTADO")||to.equals("CANCELADO")) {
            intent.begin(id,to);
            try {
                if(to.equals("ACEPTADO")) catalog.post().uri("/api/catalog/reservations/{id}",id).header(HttpHeaders.AUTHORIZATION,"Bearer "+jwt.getTokenValue()).body(Map.of("items",order.path("items"))).retrieve().toBodilessEntity();
                else catalog.delete().uri("/api/catalog/reservations/{id}",id).header(HttpHeaders.AUTHORIZATION,"Bearer "+jwt.getTokenValue()).retrieve().toBodilessEntity();
            } catch(RestClientResponseException e) { if(e.getStatusCode().is4xxClientError()) intent.rejected(id);throw new ResponseStatusException(HttpStatus.valueOf(e.getStatusCode().value()),"Reserva de stock rechazada",e); }
            catch(RestClientException e) { throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE,"No se pudo confirmar stock; reintente la misma operación",e); }
        }
        order.put("estado",to).put("version",order.path("version").asLong()+1).put("actualizadoEn",Instant.now().toString());
        if(to.equals("ENTREGADO")) order.put("entregadoEn",Instant.now().toString());
        store.put("order:"+id,"order",order);intent.finish(id);enqueue(order,jwt,request);return order;
    }
    @PutMapping("/{id}") public ObjectNode update(@PathVariable long id,@Valid @RequestBody Create input,@AuthenticationPrincipal Jwt jwt,HttpServletRequest request) {
        var order=store.lock("order:"+id);intent.guard(id,"EDIT");if(!order.path("estado").asText().equals("CREADO")) throw new ResponseStatusException(HttpStatus.CONFLICT,"Solo se puede editar un pedido creado");
        // Create a validated quote without publishing intermediate orders.
        var items=order.putArray("items");var total=BigDecimal.ZERO;var quantities=new TreeMap<Long,Integer>();for(var i:input.items()) quantities.merge(i.productId(),i.quantity(),Math::addExact);
        for(var e:quantities.entrySet()) { ObjectNode p;try { p=catalog.get().uri("/api/catalog/products/{id}",e.getKey()).header(HttpHeaders.AUTHORIZATION,"Bearer "+jwt.getTokenValue()).retrieve().body(ObjectNode.class); }catch(RestClientException ex) { throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE,"No se pudo cotizar el producto",ex); }if(p==null) throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE);var price=p.path("precio").decimalValue();total=total.add(price.multiply(BigDecimal.valueOf(e.getValue())));items.addObject().put("productId",e.getKey()).put("nombre",p.path("nombre").asText()).put("quantity",e.getValue()).put("precio",price); }
        order.put("email",input.email()).put("total",total).put("version",order.path("version").asLong()+1);store.put("order:"+id,"order",order);enqueue(order,jwt,request,"OrderUpdated");return order;
    }
    @DeleteMapping("/{id}") @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable long id,@AuthenticationPrincipal Jwt jwt,HttpServletRequest request) { change(id,new Change("CANCELADO"),jwt,request); }
    private static boolean staff(Jwt jwt) { var roles=jwt.getClaimAsStringList("roles");return roles!=null&&(roles.contains("Admin")||roles.contains("Operator")); }
    private void enqueue(ObjectNode order,Jwt jwt,HttpServletRequest request) { enqueue(order,jwt,request,OrderFlow.type(order.path("estado").asText())); }
    private void enqueue(ObjectNode order,Jwt jwt,HttpServletRequest request,String type) {
        String trace=Optional.ofNullable(request.getHeader("X-Correlation-Id")).filter(s -> s.length()<100).orElseGet(()->UUID.randomUUID().toString());
        var event=Events.envelope(type,jwt.getSubject(),request.getRemoteAddr(),trace,order);
        task("orders.events",event);task("audit.timeline",event);
        task("email.send",Events.envelope("EmailRequested",jwt.getSubject(),request.getRemoteAddr(),trace,order));
        if(type.equals("OrderAccepted")) task("kitchen.ticket",Events.envelope("KitchenTicketRequested",jwt.getSubject(),request.getRemoteAddr(),trace,order));
        if(type.equals("OrderDelivered")) task("invoice.gen",Events.envelope("InvoiceRequested",jwt.getSubject(),request.getRemoteAddr(),trace,order));
    }
    private void task(String destination,ObjectNode event) { var entry=JsonNodeFactory.instance.objectNode().put("destination",destination).put("id",UUID.randomUUID().toString()).put("attempts",0).put("createdAt",Instant.now().toString());entry.set("message",event);store.insert("outbox:"+entry.path("id").asText(),"outbox",entry); }
}
