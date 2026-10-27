package cl.duoc.pedidos360.rabbitadmin;
import org.springframework.web.bind.annotation.*;
import org.springframework.http.*;
import org.springframework.web.server.ResponseStatusException;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import java.util.*;
@RestController
@RequestMapping("/api/admin/rabbit")
public class RabbitAdminController {
    private final RabbitAdminService service;

    public RabbitAdminController(RabbitAdminService service) {
        this.service = service;
    }

    public record QueueInput(
            @NotBlank @Pattern(regexp="q\\.[a-zA-Z0-9._-]+") String name,
            @Pattern(regexp="cmd\\.[a-zA-Z0-9._-]+") String deadLetterExchange,
            @Pattern(regexp="[a-zA-Z0-9._*-]+") String deadLetterRoutingKey) {}

    public record ExchangeInput(@NotBlank @Pattern(regexp="cmd\\.[a-zA-Z0-9._-]+") String name,@NotBlank @Pattern(regexp="direct|topic") String type) {}
    public record BindingInput(@NotBlank @Pattern(regexp="q\\.[a-zA-Z0-9._-]+") String queue,@NotBlank @Pattern(regexp="cmd\\.[a-zA-Z0-9._-]+") String exchange,@NotBlank @Pattern(regexp="[a-zA-Z0-9._*-]+") String routingKey) {}

    @GetMapping("/{resource:queues|exchanges|bindings}")
    public Object list(@PathVariable String resource) {
        return service.list(resource);
    }

    @PostMapping("/queues")
    public Map<String,String> createQueue(@Valid @RequestBody QueueInput input) {
        service.createQueue(input.name(), input.deadLetterExchange(), input.deadLetterRoutingKey());
        return Map.of("name", input.name());
    }

    @DeleteMapping("/queues/{name}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deleteQueue(@PathVariable String name) {
        if (!name.matches("q\\.[a-zA-Z0-9._-]+")) throw new ResponseStatusException(HttpStatus.BAD_REQUEST);
        service.deleteQueue(name);
    }

    @PostMapping("/exchanges")
    public Map<String,String> createExchange(@Valid @RequestBody ExchangeInput input) {
        service.createExchange(input.name(), input.type());
        return Map.of("name", input.name());
    }

    @DeleteMapping("/exchanges/{name}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deleteExchange(@PathVariable String name) {
        if (!name.matches("cmd\\.[a-zA-Z0-9._-]+")) throw new ResponseStatusException(HttpStatus.BAD_REQUEST);
        service.deleteExchange(name);
    }

    @PostMapping("/bindings")
    public BindingInput bind(@Valid @RequestBody BindingInput input) {
        service.createBinding(input.queue(), input.exchange(), input.routingKey());
        return input;
    }

    @DeleteMapping("/bindings")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void unbind(@Valid @RequestBody BindingInput input) {
        service.deleteBinding(input.queue(), input.exchange(), input.routingKey());
    }
}
