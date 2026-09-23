package cl.duoc.api.dto;

public record ProductoRequest(
	String nombre,
	String descripcion,
	String categoria,
	Integer precio,
	Integer stock
) {
}
