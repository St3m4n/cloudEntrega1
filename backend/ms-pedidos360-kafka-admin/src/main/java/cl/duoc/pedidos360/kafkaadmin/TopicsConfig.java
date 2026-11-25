package cl.duoc.pedidos360.kafkaadmin;

import cl.duoc.pedidos360.common.kafka.KafkaTopics;
import java.time.Duration;
import org.apache.kafka.clients.admin.NewTopic;
import org.apache.kafka.common.config.TopicConfig;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.config.TopicBuilder;
import org.springframework.kafka.core.KafkaAdmin;

/**
 * Declaración de los tópicos según cada flujo de datos. KafkaAdmin los crea al iniciar si no existen
 * (el broker tiene auto.create.topics.enable=false). Usa los mismos parámetros que infra/kafka/compose.yml.
 * <ul>
 *   <li>orders.events: eventos de negocio; se borran a los 7 días.</li>
 *   <li>audit.timeline: compactado por pedido y borrado a los 30 días; el historial completo queda en Oracle.</li>
 *   <li>DLT: mensajes rechazados; se conservan 14 días para análisis.</li>
 * </ul>
 * Con 3 brokers: factor de replicación 3 y min.insync.replicas 2 (con acks=all tolera la caída de un broker).
 */
@Configuration
public class TopicsConfig {
    @Bean
    KafkaAdmin.NewTopics pedidosTopics(@Value("${app.kafka.partitions:3}") int partitions,
                                       @Value("${app.kafka.replication-factor:3}") int replicas,
                                       @Value("${app.kafka.min-insync-replicas:2}") String minInsync) {
        return new KafkaAdmin.NewTopics(
                topic(KafkaTopics.ORDER_EVENTS, partitions, replicas, minInsync, TopicConfig.CLEANUP_POLICY_DELETE, Duration.ofDays(7)),
                topic(KafkaTopics.AUDIT_TIMELINE, partitions, replicas, minInsync,
                        TopicConfig.CLEANUP_POLICY_COMPACT + "," + TopicConfig.CLEANUP_POLICY_DELETE, Duration.ofDays(30)),
                topic(KafkaTopics.ORDER_EVENTS_DLT, partitions, replicas, minInsync, TopicConfig.CLEANUP_POLICY_DELETE, Duration.ofDays(14)),
                topic(KafkaTopics.AUDIT_TIMELINE_DLT, partitions, replicas, minInsync, TopicConfig.CLEANUP_POLICY_DELETE, Duration.ofDays(14)));
    }

    private static NewTopic topic(String name, int partitions, int replicas, String minInsync, String cleanup, Duration retention) {
        return TopicBuilder.name(name).partitions(partitions).replicas(replicas)
                .config(TopicConfig.CLEANUP_POLICY_CONFIG, cleanup)
                .config(TopicConfig.RETENTION_MS_CONFIG, String.valueOf(retention.toMillis()))
                .config(TopicConfig.MIN_IN_SYNC_REPLICAS_CONFIG, minInsync)
                .build();
    }
}
