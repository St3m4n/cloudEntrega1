package cl.duoc.catalogo.dto;

import cl.duoc.catalogo.model.Producto;

public record ProductoResponse(
	Long id,
	String nombre,
	String descripcion,
	String categoria,
	Integer precio,
	Integer stock
) {

	public static ProductoResponse desde(Producto producto) {
		return new ProductoResponse(
			producto.getId(),
			producto.getNombre(),
			producto.getDescripcion(),
			producto.getCategoria(),
			producto.getPrecio(),
			producto.getStock()
		);
	}
}
