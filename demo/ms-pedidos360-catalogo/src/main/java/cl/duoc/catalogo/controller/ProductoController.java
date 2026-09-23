package cl.duoc.catalogo.controller;

import java.util.List;

import cl.duoc.catalogo.dto.ProductoRequest;
import cl.duoc.catalogo.dto.ProductoResponse;
import cl.duoc.catalogo.service.ProductoService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/catalog/products")
public class ProductoController {

	private final ProductoService productoService;

	public ProductoController(ProductoService productoService) {
		this.productoService = productoService;
	}

	@GetMapping
	public List<ProductoResponse> listar() {
		return productoService.listar();
	}

	@GetMapping("/{id}")
	public ProductoResponse buscarPorId(@PathVariable Long id) {
		return productoService.buscarPorId(id);
	}

	@PostMapping
	@ResponseStatus(HttpStatus.CREATED)
	public ProductoResponse crear(@Valid @RequestBody ProductoRequest request) {
		return productoService.crear(request);
	}

	@PutMapping("/{id}")
	public ProductoResponse actualizar(@PathVariable Long id, @Valid @RequestBody ProductoRequest request) {
		return productoService.actualizar(id, request);
	}

	@DeleteMapping("/{id}")
	@ResponseStatus(HttpStatus.NO_CONTENT)
	public void eliminar(@PathVariable Long id) {
		productoService.eliminar(id);
	}
}
