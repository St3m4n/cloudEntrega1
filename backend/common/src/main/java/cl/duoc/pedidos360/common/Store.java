package cl.duoc.pedidos360.common;
import com.fasterxml.jackson.databind.*;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.util.*;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.http.HttpStatus;
@Transactional
public class Store {
    public final Documents documents; private final ObjectMapper mapper;
    public Store(Documents documents,ObjectMapper mapper) { this.documents=documents;this.mapper=mapper; }
    public ObjectNode parse(String value) { try { return (ObjectNode)mapper.readTree(value); } catch(Exception e) { throw new IllegalArgumentException("JSON inválido",e); } }
    public ObjectNode get(String id) { return parse(documents.findById(id).orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND,"Recurso no encontrado")).payload); }
    public ObjectNode lock(String id) { return parse(documents.lock(id).orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND,"Recurso no encontrado")).payload); }
    public List<ObjectNode> all(String kind) { return documents.findByKindOrderByIdAsc(kind).stream().map(d -> parse(d.payload)).toList(); }
    public boolean exists(String id) { return documents.existsById(id); }
    public void put(String id,String kind,JsonNode payload) {
        var d=documents.findById(id).orElseGet(() -> new Document(id,kind,"")); d.payload=payload.toString(); documents.save(d);
    }
    public void insert(String id,String kind,JsonNode payload) { documents.saveAndFlush(new Document(id,kind,payload.toString())); }
    public void remove(String id) { documents.deleteById(id); }
}
