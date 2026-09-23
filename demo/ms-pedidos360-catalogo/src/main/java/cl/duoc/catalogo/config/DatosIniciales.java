package cl.duoc.catalogo.config;

import java.util.List;

import cl.duoc.catalogo.model.Producto;
import cl.duoc.catalogo.repository.ProductoRepository;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;

@Component
public class DatosIniciales implements CommandLineRunner {

	private final ProductoRepository productoRepository;

	public DatosIniciales(ProductoRepository productoRepository) {
		this.productoRepository = productoRepository;
	}

	@Override
	public void run(String... args) {
		if (productoRepository.count() > 0) {
			return;
		}
		productoRepository.saveAll(List.of(
			new Producto("Marraqueta", "Pan batido tradicional, por unidad", "Panaderia", 250, 120),
			new Producto("Hallulla", "Pan de masa laminada, por unidad", "Panaderia", 220, 100),
			new Producto("Croissant de mantequilla", "Hojaldre horneado del dia", "Pasteleria", 1200, 40),
			new Producto("Kuchen de manzana", "Porcion individual", "Pasteleria", 1800, 25),
			new Producto("Empanada de pino", "Horneada, receta tradicional", "Salados", 2500, 30),
			new Producto("Cafe americano", "Vaso de 12 oz", "Cafeteria", 1900, 200)
		));
	}
}
