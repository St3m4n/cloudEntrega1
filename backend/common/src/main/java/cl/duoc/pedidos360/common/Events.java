package cl.duoc.pedidos360.common;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.*;
import java.time.Instant;
import java.util.UUID;
public final class Events {
    private Events() {}
    public static ObjectNode envelope(String type, String actor,String origin,String trace, JsonNode order) {
        var n=JsonNodeFactory.instance.objectNode(); n.put("type",type);n.put("eventId",UUID.randomUUID().toString());n.put("timestamp",Instant.now().toString());
        n.put("traceId",trace);n.put("correlationId",order.path("id").asText());n.put("actor",actor);n.put("origin",origin);n.set("order",order.deepCopy()); return n;
    }
}
