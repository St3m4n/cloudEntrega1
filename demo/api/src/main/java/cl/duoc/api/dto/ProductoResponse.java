package cl.duoc.api.dto;

public record ProductoResponse(
	Long id,
	String nombre,
	String descripcion,
	String categoria,
	Integer precio,
	Integer stock
) {
}
