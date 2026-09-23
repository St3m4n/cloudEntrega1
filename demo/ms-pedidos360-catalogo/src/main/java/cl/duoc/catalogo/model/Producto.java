package cl.duoc.catalogo.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "productos")
public class Producto {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@Column(nullable = false, length = 100)
	private String nombre;

	@Column(length = 255)
	private String descripcion;

	@Column(nullable = false, length = 50)
	private String categoria;

	@Column(nullable = false)
	private Integer precio;

	@Column(nullable = false)
	private Integer stock;

	protected Producto() {
	}

	public Producto(String nombre, String descripcion, String categoria, Integer precio, Integer stock) {
		this.nombre = nombre;
		this.descripcion = descripcion;
		this.categoria = categoria;
		this.precio = precio;
		this.stock = stock;
	}

	public void actualizar(String nombre, String descripcion, String categoria, Integer precio, Integer stock) {
		this.nombre = nombre;
		this.descripcion = descripcion;
		this.categoria = categoria;
		this.precio = precio;
		this.stock = stock;
	}

	public Long getId() {
		return id;
	}

	public String getNombre() {
		return nombre;
	}

	public String getDescripcion() {
		return descripcion;
	}

	public String getCategoria() {
		return categoria;
	}

	public Integer getPrecio() {
		return precio;
	}

	public Integer getStock() {
		return stock;
	}
}
