package cl.duoc.api.service;

import java.util.List;

import cl.duoc.api.dto.ProductoRequest;
import cl.duoc.api.dto.ProductoResponse;
import cl.duoc.api.repository.CatalogoRepository;
import org.springframework.stereotype.Service;

@Service
public class CatalogoService {

	private final CatalogoRepository catalogoRepository;

	public CatalogoService(CatalogoRepository catalogoRepository) {
		this.catalogoRepository = catalogoRepository;
	}

	public List<ProductoResponse> listar() {
		return catalogoRepository.listar();
	}

	public ProductoResponse buscarPorId(Long id) {
		return catalogoRepository.buscarPorId(id);
	}

	public ProductoResponse crear(ProductoRequest request) {
		return catalogoRepository.crear(request);
	}

	public ProductoResponse actualizar(Long id, ProductoRequest request) {
		return catalogoRepository.actualizar(id, request);
	}

	public void eliminar(Long id) {
		catalogoRepository.eliminar(id);
	}
}
