package cl.duoc.catalogo.repository;

import cl.duoc.catalogo.model.Producto;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ProductoRepository extends JpaRepository<Producto, Long> {
}
