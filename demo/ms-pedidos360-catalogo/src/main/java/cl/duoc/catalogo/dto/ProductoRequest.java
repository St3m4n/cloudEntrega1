package cl.duoc.catalogo.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;

public record ProductoRequest(
	@NotBlank @Size(max = 100) String nombre,
	@Size(max = 255) String descripcion,
	@NotBlank @Size(max = 50) String categoria,
	@NotNull @PositiveOrZero Integer precio,
	@NotNull @PositiveOrZero Integer stock
) {
}
