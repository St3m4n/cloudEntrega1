package cl.duoc.pedidos360.orders;
import cl.duoc.pedidos360.common.Store;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import org.springframework.stereotype.Component;
import org.springframework.transaction.*;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.http.HttpStatus;
/** Durable intent prevents editing/changing a different state after an ambiguous remote timeout. */
@Component
public class StockIntent {
    private final Store store;private final TransactionTemplate independent;
    public StockIntent(Store store,PlatformTransactionManager manager) { this.store=store;independent=new TransactionTemplate(manager);independent.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW); }
    public void guard(long id,String target) {
        String key="stock-intent:"+id;
        if(store.exists(key)&&!store.get(key).path("target").asText().equals(target)) throw new ResponseStatusException(HttpStatus.CONFLICT,"Stock pendiente de confirmación: reintente primero "+store.get(key).path("target").asText());
    }
    public void begin(long id,String target) { guard(id,target);independent.executeWithoutResult(s->{ String key="stock-intent:"+id;if(!store.exists(key)) store.insert(key,"stock-intent",JsonNodeFactory.instance.objectNode().put("target",target)); }); }
    public void finish(long id) { if(store.exists("stock-intent:"+id)) store.remove("stock-intent:"+id); }
    public void rejected(long id) { independent.executeWithoutResult(s-> { if(store.exists("stock-intent:"+id)) store.remove("stock-intent:"+id); }); }
}
