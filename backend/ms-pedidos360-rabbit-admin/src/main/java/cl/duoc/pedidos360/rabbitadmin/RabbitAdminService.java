package cl.duoc.pedidos360.rabbitadmin;

import java.net.URI;
import java.net.http.HttpClient;
import java.time.Duration;
import java.util.Map;

import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.DirectExchange;
import org.springframework.amqp.core.QueueBuilder;
import org.springframework.amqp.core.TopicExchange;
import org.springframework.amqp.rabbit.connection.ConnectionFactory;
import org.springframework.amqp.rabbit.core.RabbitAdmin;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

/** Application service that owns RabbitMQ administration operations. */
@Service
public class RabbitAdminService {
    private final RabbitAdmin admin;
    private final RestClient management;
    private final String managementUrl;
    private final int quorumSize;

    public RabbitAdminService(
            ConnectionFactory factory,
            @Value("${RABBIT_MANAGEMENT_URL:http://localhost:15672}") String url,
            @Value("${spring.rabbitmq.username}") String user,
            @Value("${spring.rabbitmq.password}") String password,
            @Value("${app.rabbit-quorum-size:1}") int quorumSize) {
        admin = new RabbitAdmin(factory);
        managementUrl = url;
        this.quorumSize = quorumSize;
        var httpFactory = new JdkClientHttpRequestFactory(
                HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(3)).build());
        httpFactory.setReadTimeout(Duration.ofSeconds(10));
        management = RestClient.builder()
                .requestFactory(httpFactory)
                .defaultHeaders(headers -> headers.setBasicAuth(user, password))
                .build();
    }

    public Object list(String resource) {
        return management.get()
                .uri(URI.create(managementUrl + "/api/" + resource + "/%2F"))
                .retrieve()
                .body(Object.class);
    }

    public void createQueue(String name, String deadLetterExchange, String deadLetterRoutingKey) {
        var builder = QueueBuilder.durable(name)
                .quorum()
                .withArgument("x-quorum-initial-group-size", quorumSize);
        if (deadLetterExchange != null) {
            builder.deadLetterExchange(deadLetterExchange);
        }
        if (deadLetterRoutingKey != null) {
            builder.deadLetterRoutingKey(deadLetterRoutingKey);
        }
        admin.declareQueue(builder.build());
    }

    public void deleteQueue(String name) {
        admin.deleteQueue(name);
    }

    public void createExchange(String name, String type) {
        admin.declareExchange(type.equals("direct")
                ? new DirectExchange(name, true, false)
                : new TopicExchange(name, true, false));
    }

    public void deleteExchange(String name) {
        admin.deleteExchange(name);
    }

    public void createBinding(String queue, String exchange, String routingKey) {
        admin.declareBinding(new Binding(queue, Binding.DestinationType.QUEUE, exchange, routingKey, Map.of()));
    }

    public void deleteBinding(String queue, String exchange, String routingKey) {
        admin.removeBinding(new Binding(queue, Binding.DestinationType.QUEUE, exchange, routingKey, Map.of()));
    }
}
