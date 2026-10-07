package cl.duoc.pedidos360.common;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.http.*;
import org.springframework.dao.*;
import org.springframework.web.bind.MethodArgumentNotValidException;
@RestControllerAdvice
public class ApiErrors {
    @ExceptionHandler(ResponseStatusException.class)
    ResponseEntity<ProblemDetail> status(ResponseStatusException e) {
        return ResponseEntity.status(e.getStatusCode()).body(ProblemDetail.forStatusAndDetail(e.getStatusCode(), e.getReason()==null ? "Error" : e.getReason()));
    }
    @ExceptionHandler({IllegalArgumentException.class, MethodArgumentNotValidException.class})
    ResponseEntity<ProblemDetail> invalid(Exception e) { return ResponseEntity.badRequest().body(ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST,"Datos inválidos: " + e.getMessage())); }
    @ExceptionHandler({OptimisticLockingFailureException.class, DataIntegrityViolationException.class, PessimisticLockingFailureException.class})
    ResponseEntity<ProblemDetail> conflict(Exception e) { return ResponseEntity.status(409).body(ProblemDetail.forStatusAndDetail(HttpStatus.CONFLICT,"Operación concurrente; vuelva a consultar y reintente")); }
    @ExceptionHandler({org.springframework.web.client.RestClientException.class,org.springframework.amqp.AmqpException.class,java.util.concurrent.TimeoutException.class})
    ResponseEntity<ProblemDetail> unavailable(Exception e) { return ResponseEntity.status(503).body(ProblemDetail.forStatusAndDetail(HttpStatus.SERVICE_UNAVAILABLE,"Servicio externo no disponible")); }
    @ExceptionHandler(java.util.concurrent.ExecutionException.class)
    ResponseEntity<ProblemDetail> kafka(java.util.concurrent.ExecutionException e) {
        var cause=e.getCause();HttpStatus status=cause instanceof org.apache.kafka.common.errors.TopicExistsException ? HttpStatus.CONFLICT : cause instanceof org.apache.kafka.common.errors.UnknownTopicOrPartitionException ? HttpStatus.NOT_FOUND : cause instanceof org.apache.kafka.common.errors.InvalidPartitionsException || cause instanceof org.apache.kafka.common.errors.InvalidConfigurationException ? HttpStatus.BAD_REQUEST : HttpStatus.SERVICE_UNAVAILABLE;
        return ResponseEntity.status(status).body(ProblemDetail.forStatusAndDetail(status,"Operación Kafka rechazada: "+cause.getClass().getSimpleName()));
    }
}
