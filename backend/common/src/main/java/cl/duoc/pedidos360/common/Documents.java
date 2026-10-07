package cl.duoc.pedidos360.common;
import java.util.*;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;
import jakarta.persistence.LockModeType;
public interface Documents extends JpaRepository<Document,String> {
    List<Document> findByKindOrderByIdAsc(String kind);
    @Lock(LockModeType.PESSIMISTIC_WRITE) @Query("select d from Document d where d.id=:id")
    Optional<Document> lock(@Param("id") String id);
}
