package cl.duoc.api.controller;

import java.util.List;

import cl.duoc.api.dto.ProductoRequest;
import cl.duoc.api.dto.ProductoResponse;
import cl.duoc.api.service.CatalogoService;
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
public class CatalogoController {

	private final CatalogoService catalogoService;

	public CatalogoController(CatalogoService catalogoService) {
		this.catalogoService = catalogoService;
	}

	@GetMapping
	public List<ProductoResponse> listar() {
		return catalogoService.listar();
	}

	@GetMapping("/{id}")
	public ProductoResponse buscarPorId(@PathVariable Long id) {
		return catalogoService.buscarPorId(id);
	}

	@PostMapping
	@ResponseStatus(HttpStatus.CREATED)
	public ProductoResponse crear(@RequestBody ProductoRequest request) {
		return catalogoService.crear(request);
	}

	@PutMapping("/{id}")
	public ProductoResponse actualizar(@PathVariable Long id, @RequestBody ProductoRequest request) {
		return catalogoService.actualizar(id, request);
	}

	@DeleteMapping("/{id}")
	@ResponseStatus(HttpStatus.NO_CONTENT)
	public void eliminar(@PathVariable Long id) {
		catalogoService.eliminar(id);
	}
}
