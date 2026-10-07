package cl.duoc.pedidos360.catalog;
import cl.duoc.pedidos360.common.*;
import com.fasterxml.jackson.databind.node.*;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import org.springframework.web.bind.annotation.*;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.http.HttpStatus;
import java.math.BigDecimal;
import java.util.*;
import java.util.concurrent.ThreadLocalRandom;

@RestController @RequestMapping("/api/catalog") @Transactional
public class CatalogController {
    private final Store store;
    public CatalogController(Store store) { this.store=store; }
    public record ProductInput(@NotBlank @Size(max=100) String nombre,@Size(max=255) String descripcion,@NotBlank @Size(max=50) String categoria,@NotNull @DecimalMin("0.01") BigDecimal precio,@NotNull @Min(0) Integer stock) {}
    public record Line(@Positive long productId,@Min(1) int quantity) {}
    public record Reservation(@NotEmpty @Size(max=100) List<@Valid Line> items) {}
    @GetMapping("/products") public List<ObjectNode> list() { return store.all("product").stream().filter(p -> p.path("activo").asBoolean(true)).toList(); }
    @GetMapping("/products/{id}") public ObjectNode get(@PathVariable long id) { var p=store.get("product:"+id); active(p); return p; }
    @PostMapping("/products") @ResponseStatus(HttpStatus.CREATED)
    public ObjectNode create(@Valid @RequestBody ProductInput input) {
        long id=ThreadLocalRandom.current().nextLong(1,9_000_000_000_000_000L);
        var p=JsonNodeFactory.instance.objectNode().put("id",id).put("activo",true); fields(p,input); store.insert("product:"+id,"product",p); return p;
    }
    @PutMapping("/products/{id}") public ObjectNode update(@PathVariable long id,@Valid @RequestBody ProductInput input) { var p=store.lock("product:"+id);active(p);fields(p,input);store.put("product:"+id,"product",p);return p; }
    @DeleteMapping("/products/{id}") @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable long id) { var p=store.lock("product:"+id);p.put("activo",false);store.put("product:"+id,"product",p); }
    @PostMapping("/reservations/{orderId}") public ObjectNode reserve(@PathVariable long orderId,@Valid @RequestBody Reservation input) {
        String key="reservation:"+orderId;
        var quantities=new TreeMap<Long,Integer>();
        for(var line:input.items()) quantities.merge(line.productId(),line.quantity(),Math::addExact);
        if(store.exists(key)) { var r=store.lock(key); if(r.path("released").asBoolean()) throw new ResponseStatusException(HttpStatus.CONFLICT,"Reserva ya liberada");var previous=new TreeMap<Long,Integer>();r.path("items").forEach(i->previous.put(i.path("productId").asLong(),i.path("quantity").asInt()));if(!previous.equals(quantities)) throw new ResponseStatusException(HttpStatus.CONFLICT,"Pedido ya reservado con otros productos");return r; }
        var r=JsonNodeFactory.instance.objectNode().put("id",orderId).put("released",false); var items=r.putArray("items");
        for(var entry:quantities.entrySet()) {
            var p=store.lock("product:"+entry.getKey());active(p);
            if(p.path("stock").asInt()<entry.getValue()) throw new ResponseStatusException(HttpStatus.CONFLICT,"Stock insuficiente para "+p.path("nombre").asText());
            p.put("stock",p.path("stock").asInt()-entry.getValue());store.put("product:"+entry.getKey(),"product",p);
            items.addObject().put("productId",entry.getKey()).put("quantity",entry.getValue());
        }
        store.insert(key,"reservation",r);return r;
    }
    @DeleteMapping("/reservations/{orderId}") @ResponseStatus(HttpStatus.NO_CONTENT)
    public void release(@PathVariable long orderId) {
        String key="reservation:"+orderId; if(!store.exists(key)) return;
        var r=store.lock(key);if(r.path("released").asBoolean()) return;
        var quantities=new TreeMap<Long,Integer>();r.path("items").forEach(i -> quantities.put(i.path("productId").asLong(),i.path("quantity").asInt()));
        for(var entry:quantities.entrySet()) { var p=store.lock("product:"+entry.getKey());p.put("stock",Math.addExact(p.path("stock").asInt(),entry.getValue()));store.put("product:"+entry.getKey(),"product",p); }
        r.put("released",true);store.put(key,"reservation",r);
    }
    private static void active(ObjectNode p) { if(!p.path("activo").asBoolean(true)) throw new ResponseStatusException(HttpStatus.NOT_FOUND,"Producto retirado"); }
    private static void fields(ObjectNode p,ProductInput i) { p.put("nombre",i.nombre());p.put("descripcion",i.descripcion());p.put("categoria",i.categoria());p.put("precio",i.precio());p.put("stock",i.stock()); }
}
