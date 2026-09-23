package cl.duoc.api.repository;

import java.util.List;

import cl.duoc.api.dto.ProductoRequest;
import cl.duoc.api.dto.ProductoResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.stereotype.Repository;
import org.springframework.web.client.RestClient;

@Repository
public class CatalogoRepository {

	private static final String PRODUCTOS = "/api/catalog/products";

	private final RestClient restClient;

	public CatalogoRepository(
		RestClient.Builder restClientBuilder,
		@Value("${microservices.catalogo.base-url}")
		String baseUrl) {
		this.restClient = restClientBuilder
			.baseUrl(baseUrl)
			.build();
	}

	public List<ProductoResponse> listar() {
		return restClient.get()
			.uri(PRODUCTOS)
			.retrieve()
			.body(new ParameterizedTypeReference<List<ProductoResponse>>() {});
	}

	public ProductoResponse buscarPorId(Long id) {
		return restClient.get()
			.uri(PRODUCTOS + "/{id}", id)
			.retrieve()
			.body(ProductoResponse.class);
	}

	public ProductoResponse crear(ProductoRequest request) {
		return restClient.post()
			.uri(PRODUCTOS)
			.body(request)
			.retrieve()
			.body(ProductoResponse.class);
	}

	public ProductoResponse actualizar(Long id, ProductoRequest request) {
		return restClient.put()
			.uri(PRODUCTOS + "/{id}", id)
			.body(request)
			.retrieve()
			.body(ProductoResponse.class);
	}

	public void eliminar(Long id) {
		restClient.delete()
			.uri(PRODUCTOS + "/{id}", id)
			.retrieve()
			.toBodilessEntity();
	}
}
