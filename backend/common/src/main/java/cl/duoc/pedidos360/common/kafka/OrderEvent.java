package cl.duoc.pedidos360.common.kafka;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.time.Instant;

/**
 * DTO del evento de dominio que viaja por {@code orders.events} y {@code audit.timeline}.
 * Se serializa como JSON con {@code JsonSerializer} y se lee con {@code JsonDeserializer};
 * las anotaciones de validación se aplican en los {@code @KafkaListener} con {@code @Valid}.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonIgnoreProperties(ignoreUnknown = true)
public record OrderEvent(
        @NotBlank String type,
        @NotBlank String eventId,
        @NotNull @JsonFormat(shape = JsonFormat.Shape.STRING) Instant timestamp,
        String traceId,
        @NotBlank String correlationId,
        String actor,
        String origin,
        @NotNull @Valid OrderSnapshot order) {
}
