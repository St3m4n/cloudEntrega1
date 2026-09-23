package cl.duoc.catalogo;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import cl.duoc.catalogo.dto.ProductoRequest;
import cl.duoc.catalogo.dto.ProductoResponse;
import cl.duoc.catalogo.service.ProductoService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

@SpringBootTest
class ProductoServiceTests {

	@Autowired
	private ProductoService productoService;

	@Test
	void cargaProductosIniciales() {
		assertThat(productoService.listar()).isNotEmpty();
	}

	@Test
	void creaActualizaYEliminaProducto() {
		ProductoResponse creado = productoService.crear(
			new ProductoRequest("Pan amasado", "Por unidad", "Panaderia", 300, 50));
		assertThat(creado.id()).isNotNull();

		ProductoResponse actualizado = productoService.actualizar(creado.id(),
			new ProductoRequest("Pan amasado", "Por unidad", "Panaderia", 350, 45));
		assertThat(actualizado.precio()).isEqualTo(350);
		assertThat(actualizado.stock()).isEqualTo(45);

		productoService.eliminar(creado.id());
		assertThatThrownBy(() -> productoService.buscarPorId(creado.id()))
			.isInstanceOfSatisfying(ResponseStatusException.class,
				e -> assertThat(e.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND));
	}
}
