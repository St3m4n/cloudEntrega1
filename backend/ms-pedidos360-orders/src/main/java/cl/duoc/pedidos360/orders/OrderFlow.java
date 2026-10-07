package cl.duoc.pedidos360.orders;
import java.util.*;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;
public final class OrderFlow {
    public static final Set<String> STATES=Set.of("CREADO","ACEPTADO","EN_PREPARACION","DESPACHADO","ENTREGADO","CANCELADO");
    private static final Map<String,Set<String>> NEXT=Map.of("CREADO",Set.of("ACEPTADO","CANCELADO"),"ACEPTADO",Set.of("EN_PREPARACION","CANCELADO"),"EN_PREPARACION",Set.of("DESPACHADO","CANCELADO"),"DESPACHADO",Set.of("ENTREGADO"),"ENTREGADO",Set.of(),"CANCELADO",Set.of());
    public static void validate(String from,String to) { if(!STATES.contains(to)) throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"Estado inválido"); if(!from.equals(to)&&!NEXT.getOrDefault(from,Set.of()).contains(to)) throw new ResponseStatusException(HttpStatus.CONFLICT,"Transición inválida: "+from+" -> "+to); }
    public static String type(String state) { return Map.of("CREADO","OrderCreated","ACEPTADO","OrderAccepted","EN_PREPARACION","OrderPreparing","DESPACHADO","OrderDispatched","ENTREGADO","OrderDelivered","CANCELADO","OrderCancelled").get(state); }
    private OrderFlow() {}
}
