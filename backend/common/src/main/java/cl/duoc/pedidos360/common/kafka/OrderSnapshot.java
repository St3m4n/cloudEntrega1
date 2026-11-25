package cl.duoc.pedidos360.common.kafka;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.PositiveOrZero;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

/** Estado del pedido incluido en cada {@link OrderEvent}. La versión permite descartar eventos antiguos o duplicados. */
@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonIgnoreProperties(ignoreUnknown = true)
public record OrderSnapshot(
        @NotNull @Positive Long id,
        String cliente,
        String owner,
        String email,
        @NotBlank String estado,
        @NotNull @Positive Long version,
        @PositiveOrZero BigDecimal total,
        @JsonFormat(shape = JsonFormat.Shape.STRING) Instant creadoEn,
        @JsonFormat(shape = JsonFormat.Shape.STRING) Instant actualizadoEn,
        @JsonFormat(shape = JsonFormat.Shape.STRING) Instant entregadoEn,
        List<@Valid OrderItem> items) {

    @JsonInclude(JsonInclude.Include.NON_NULL)
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record OrderItem(@NotNull @Positive Long productId, String nombre, @NotNull @Positive Integer quantity,
                            @PositiveOrZero BigDecimal precio) {
    }
}
