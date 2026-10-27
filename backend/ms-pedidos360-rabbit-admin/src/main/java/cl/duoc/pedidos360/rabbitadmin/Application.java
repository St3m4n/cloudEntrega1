package cl.duoc.pedidos360.rabbitadmin;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Import;
import org.springframework.scheduling.annotation.EnableScheduling;
import cl.duoc.pedidos360.common.SecurityConfig;
import cl.duoc.pedidos360.common.ApiErrors;
import cl.duoc.pedidos360.common.RabbitTopology;

@SpringBootApplication(exclude={org.springframework.boot.autoconfigure.jdbc.DataSourceAutoConfiguration.class,org.springframework.boot.autoconfigure.orm.jpa.HibernateJpaAutoConfiguration.class,org.springframework.boot.autoconfigure.data.jpa.JpaRepositoriesAutoConfiguration.class})
@Import({SecurityConfig.class,ApiErrors.class,RabbitTopology.class})
@EnableScheduling
public class Application { public static void main(String[] args) { SpringApplication.run(Application.class,args); } }
