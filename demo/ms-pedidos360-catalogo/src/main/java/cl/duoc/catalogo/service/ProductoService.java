package cl.duoc.catalogo.service;

import java.util.List;

import cl.duoc.catalogo.dto.ProductoRequest;
import cl.duoc.catalogo.dto.ProductoResponse;
import cl.duoc.catalogo.model.Producto;
import cl.duoc.catalogo.repository.ProductoRepository;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
public class ProductoService {

	private final ProductoRepository productoRepository;

	public ProductoService(ProductoRepository productoRepository) {
		this.productoRepository = productoRepository;
	}

	@Transactional(readOnly = true)
	public List<ProductoResponse> listar() {
		return productoRepository.findAll(Sort.by("id")).stream()
			.map(ProductoResponse::desde)
			.toList();
	}

	@Transactional(readOnly = true)
	public ProductoResponse buscarPorId(Long id) {
		return ProductoResponse.desde(obtener(id));
	}

	@Transactional
	public ProductoResponse crear(ProductoRequest request) {
		Producto producto = new Producto(
			request.nombre(),
			request.descripcion(),
			request.categoria(),
			request.precio(),
			request.stock()
		);
		return ProductoResponse.desde(productoRepository.save(producto));
	}

	@Transactional
	public ProductoResponse actualizar(Long id, ProductoRequest request) {
		Producto producto = obtener(id);
		producto.actualizar(
			request.nombre(),
			request.descripcion(),
			request.categoria(),
			request.precio(),
			request.stock()
		);
		return ProductoResponse.desde(producto);
	}

	@Transactional
	public void eliminar(Long id) {
		productoRepository.delete(obtener(id));
	}

	private Producto obtener(Long id) {
		return productoRepository.findById(id)
			.orElseThrow(() -> new ResponseStatusException(
				HttpStatus.NOT_FOUND, "Producto " + id + " no encontrado"));
	}
}
