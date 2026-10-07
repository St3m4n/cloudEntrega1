package cl.duoc.pedidos360.catalog;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Import;
import org.springframework.scheduling.annotation.EnableScheduling;
import cl.duoc.pedidos360.common.SecurityConfig;
import cl.duoc.pedidos360.common.ApiErrors;
import cl.duoc.pedidos360.common.PersistenceConfig;
@SpringBootApplication
@Import({SecurityConfig.class,ApiErrors.class,PersistenceConfig.class})
@EnableScheduling
public class Application { public static void main(String[] args) { SpringApplication.run(Application.class,args); } }
