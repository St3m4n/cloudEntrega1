package cl.duoc.pedidos360.rabbitadmin;
import cl.duoc.pedidos360.common.RabbitTopology;
import org.springframework.context.annotation.Import;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.amqp.rabbit.core.RabbitAdmin;
import org.springframework.amqp.rabbit.connection.ConnectionFactory;
import org.springframework.amqp.core.*;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.client.RestClient;
import org.springframework.http.*;
import org.springframework.web.server.ResponseStatusException;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import java.util.*;
@RestController @RequestMapping("/api/admin/rabbit") @Import(RabbitTopology.class)
public class RabbitAdminController {
    private final RabbitAdmin admin;private final RestClient management;private final String managementUrl;
    public RabbitAdminController(ConnectionFactory factory,@Value("${RABBIT_MANAGEMENT_URL:http://localhost:15672}") String url,@Value("${spring.rabbitmq.username}") String user,@Value("${spring.rabbitmq.password}") String password) { admin=new RabbitAdmin(factory);managementUrl=url;var factoryHttp=new org.springframework.http.client.JdkClientHttpRequestFactory(java.net.http.HttpClient.newBuilder().connectTimeout(java.time.Duration.ofSeconds(3)).build());factoryHttp.setReadTimeout(java.time.Duration.ofSeconds(10));management=RestClient.builder().requestFactory(factoryHttp).defaultHeaders(h->h.setBasicAuth(user,password)).build(); }
    public record QueueInput(@Pattern(regexp="q\\.[a-zA-Z0-9._-]+") @NotBlank String name,String deadLetterExchange,String deadLetterRoutingKey) {}
    public record ExchangeInput(@NotBlank @Pattern(regexp="cmd\\.[a-zA-Z0-9._-]+") String name,@NotBlank @Pattern(regexp="direct|topic") String type) {}
    public record BindingInput(@NotBlank @Pattern(regexp="q\\.[a-zA-Z0-9._-]+") String queue,@NotBlank @Pattern(regexp="cmd\\.[a-zA-Z0-9._-]+") String exchange,@NotBlank String routingKey) {}
    @GetMapping("/{resource:queues|exchanges|bindings}") public Object list(@PathVariable String resource) { return management.get().uri(java.net.URI.create(managementUrl+"/api/"+resource+"/%2F")).retrieve().body(Object.class); }
    @PostMapping("/queues") public Map<String,String> createQueue(@Valid @RequestBody QueueInput input) { var builder=QueueBuilder.durable(input.name()).quorum().withArgument("x-quorum-initial-group-size",2);if(input.deadLetterExchange()!=null) builder.deadLetterExchange(input.deadLetterExchange());if(input.deadLetterRoutingKey()!=null) builder.deadLetterRoutingKey(input.deadLetterRoutingKey());admin.declareQueue(builder.build());return Map.of("name",input.name()); }
    @DeleteMapping("/queues/{name}") @ResponseStatus(HttpStatus.NO_CONTENT) public void deleteQueue(@PathVariable String name) { if(!name.matches("q\\.[a-zA-Z0-9._-]+")) throw new ResponseStatusException(HttpStatus.BAD_REQUEST);admin.deleteQueue(name); }
    @PostMapping("/exchanges") public Map<String,String> createExchange(@Valid @RequestBody ExchangeInput input) { admin.declareExchange(input.type().equals("direct")?new DirectExchange(input.name(),true,false):new TopicExchange(input.name(),true,false));return Map.of("name",input.name()); }
    @DeleteMapping("/exchanges/{name}") @ResponseStatus(HttpStatus.NO_CONTENT) public void deleteExchange(@PathVariable String name) { if(!name.matches("cmd\\.[a-zA-Z0-9._-]+")) throw new ResponseStatusException(HttpStatus.BAD_REQUEST);admin.deleteExchange(name); }
    @PostMapping("/bindings") public BindingInput bind(@Valid @RequestBody BindingInput input) { admin.declareBinding(binding(input));return input; }
    @DeleteMapping("/bindings") @ResponseStatus(HttpStatus.NO_CONTENT) public void unbind(@Valid @RequestBody BindingInput input) { admin.removeBinding(binding(input)); }
    private static Binding binding(BindingInput input) { return new Binding(input.queue(),Binding.DestinationType.QUEUE,input.exchange(),input.routingKey(),Map.of()); }
}
