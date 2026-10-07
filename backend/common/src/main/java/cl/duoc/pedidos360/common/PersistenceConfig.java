package cl.duoc.pedidos360.common;
import org.springframework.context.annotation.*;
import org.springframework.boot.autoconfigure.domain.EntityScan;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
@Configuration @EntityScan(basePackageClasses=Document.class) @EnableJpaRepositories(basePackageClasses=Documents.class)
public class PersistenceConfig {
    @Bean Store store(Documents documents,com.fasterxml.jackson.databind.ObjectMapper mapper) { return new Store(documents,mapper); }
}
