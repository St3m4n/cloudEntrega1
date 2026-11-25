package cl.duoc.pedidos360.kafkaadmin;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.common.header.internals.RecordHeaders;
import org.junit.jupiter.api.Test;
import org.springframework.kafka.support.KafkaHeaders;

class KafkaMessageMonitorTests {
    @Test
    void decodesDeadLetterHeadersAndKeepsOnlyRecentMessages() {
        var monitor = new KafkaMessageMonitor();
        var headers = new RecordHeaders();
        headers.add(KafkaHeaders.DLT_ORIGINAL_TOPIC, "orders.events".getBytes(StandardCharsets.UTF_8));
        headers.add(KafkaHeaders.DLT_ORIGINAL_PARTITION, ByteBuffer.allocate(4).putInt(2).array());
        headers.add(KafkaHeaders.DLT_ORIGINAL_OFFSET, ByteBuffer.allocate(8).putLong(41L).array());
        headers.add(KafkaHeaders.DLT_ORIGINAL_CONSUMER_GROUP, "pedidos360-report".getBytes(StandardCharsets.UTF_8));
        headers.add(KafkaHeaders.DLT_EXCEPTION_MESSAGE, "Evento inválido".getBytes(StandardCharsets.UTF_8));
        headers.add("x-dlt-reason", "DTO inválido: order.id must be greater than 0".getBytes(StandardCharsets.UTF_8));
        var dlt = new ConsumerRecord<>("orders.events.ms-pedidos360-report.DLT", 2, 0L, 1000L,
                org.apache.kafka.common.record.TimestampType.CREATE_TIME, 0, 0, "k", "{\"bad\":true}", headers, java.util.Optional.empty());
        monitor.onMessage(dlt);
        for (int i = 0; i < KafkaMessageMonitor.LIMIT + 5; i++)
            monitor.onMessage(new ConsumerRecord<>("orders.events", i % 3, i, "k" + i, "{\"n\":" + i + "}"));

        var snapshot = monitor.snapshot();
        @SuppressWarnings("unchecked") var dltView = (Map<String, Object>) snapshot.get("orders.events.ms-pedidos360-report.DLT");
        @SuppressWarnings("unchecked") var dltMessages = (List<KafkaMessageMonitor.KafkaMessage>) dltView.get("messages");
        assertThat(dltView.get("deadLetter")).isEqualTo(true);
        assertThat(dltMessages.getFirst().deadLetter()).containsEntry("originalTopic", "orders.events")
                .containsEntry("originalPartition", "2").containsEntry("originalOffset", "41")
                .containsEntry("consumerGroup", "pedidos360-report").containsEntry("message", "Evento inválido").containsEntry("reason", "DTO inválido: order.id must be greater than 0");
        @SuppressWarnings("unchecked") var events = (List<KafkaMessageMonitor.KafkaMessage>) ((Map<String, Object>) snapshot.get("orders.events")).get("messages");
        assertThat(events).hasSize(KafkaMessageMonitor.LIMIT);
        assertThat(events.getFirst().deadLetter()).isNull();
    }
}
