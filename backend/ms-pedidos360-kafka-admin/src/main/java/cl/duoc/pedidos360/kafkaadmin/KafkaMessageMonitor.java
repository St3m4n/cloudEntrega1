package cl.duoc.pedidos360.kafkaadmin;

import cl.duoc.pedidos360.common.kafka.KafkaTopics;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.ArrayDeque;
import java.util.Comparator;
import java.util.Deque;
import java.util.LinkedHashMap;
import java.util.Map;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.common.TopicPartition;
import org.apache.kafka.common.header.Headers;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.listener.ConsumerSeekAware;
import org.springframework.kafka.support.KafkaHeaders;
import org.springframework.stereotype.Component;

/**
 * Monitor de mensajes para el front. Un {@code @KafkaListener} con grupo propio lee los tópicos principales y los DLT
 * como texto (sin afectar a los consumidores de negocio) y guarda los últimos mensajes de cada tópico en memoria.
 * Al asignarse las particiones vuelve al inicio, así el historial se reconstruye desde Kafka cuando el servicio reinicia.
 */
@Component
public class KafkaMessageMonitor implements ConsumerSeekAware {
    static final int LIMIT = 50;

    public record KafkaMessage(String topic, int partition, long offset, String key, Instant timestamp, String value,
                               Map<String, String> deadLetter) {}

    private final Map<String, Deque<KafkaMessage>> messages = new LinkedHashMap<>();

    public KafkaMessageMonitor() {
        KafkaTopics.ALL.forEach(topic -> messages.put(topic, new ArrayDeque<>()));
    }

    @KafkaListener(topics = {KafkaTopics.ORDER_EVENTS, KafkaTopics.AUDIT_TIMELINE, KafkaTopics.ORDER_EVENTS_DLT,
            KafkaTopics.AUDIT_TIMELINE_DLT}, groupId = "${app.kafka.monitor-group-id}",
            autoStartup = "${app.kafka.monitor-enabled:true}")
    public void onMessage(ConsumerRecord<String, String> record) {
        String value = record.value();
        if (value != null && value.length() > 2000) value = value.substring(0, 2000) + "...";
        add(new KafkaMessage(record.topic(), record.partition(), record.offset(), record.key(),
                Instant.ofEpochMilli(record.timestamp()), value, deadLetterInfo(record.headers())));
    }

    @Override
    public void onPartitionsAssigned(Map<TopicPartition, Long> assignments, ConsumerSeekCallback callback) {
        synchronized (this) {
            for (var tp : assignments.keySet()) {
                var buffer = messages.get(tp.topic());
                if (buffer != null) buffer.removeIf(m -> m.partition() == tp.partition());
            }
        }
        callback.seekToBeginning(assignments.keySet());
    }

    synchronized void add(KafkaMessage message) {
        var buffer = messages.computeIfAbsent(message.topic(), t -> new ArrayDeque<>());
        buffer.removeIf(m -> m.partition() == message.partition() && m.offset() == message.offset());
        buffer.addLast(message);
        while (buffer.size() > LIMIT) buffer.removeFirst();
    }

    /** Últimos mensajes por tópico, del más reciente al más antiguo. */
    public synchronized Map<String, Object> snapshot() {
        var result = new LinkedHashMap<String, Object>();
        messages.forEach((topic, buffer) -> result.put(topic, Map.of(
                "deadLetter", KafkaTopics.DEAD_LETTER.contains(topic),
                "messages", buffer.stream()
                        .sorted(Comparator.comparing(KafkaMessage::timestamp).thenComparing(KafkaMessage::offset).reversed())
                        .toList())));
        return result;
    }

    static Map<String, String> deadLetterInfo(Headers headers) {
        var info = new LinkedHashMap<String, String>();
        text(headers, KafkaHeaders.DLT_ORIGINAL_TOPIC, "originalTopic", info);
        number(headers, KafkaHeaders.DLT_ORIGINAL_PARTITION, "originalPartition", info);
        number(headers, KafkaHeaders.DLT_ORIGINAL_OFFSET, "originalOffset", info);
        text(headers, KafkaHeaders.DLT_ORIGINAL_CONSUMER_GROUP, "consumerGroup", info);
        text(headers, KafkaHeaders.DLT_EXCEPTION_FQCN, "exception", info);
        text(headers, KafkaHeaders.DLT_EXCEPTION_CAUSE_FQCN, "cause", info);
        text(headers, KafkaHeaders.DLT_EXCEPTION_MESSAGE, "message", info);
        text(headers, "x-dlt-reason", "reason", info);
        return info.isEmpty() ? null : info;
    }

    private static void text(Headers headers, String name, String label, Map<String, String> info) {
        var header = headers.lastHeader(name);
        if (header == null || header.value() == null) return;
        String value = new String(header.value(), StandardCharsets.UTF_8);
        info.put(label, value.length() > 600 ? value.substring(0, 600) + "..." : value);
    }

    private static void number(Headers headers, String name, String label, Map<String, String> info) {
        var header = headers.lastHeader(name);
        if (header == null || header.value() == null) return;
        byte[] bytes = header.value();
        if (bytes.length == Integer.BYTES) info.put(label, String.valueOf(ByteBuffer.wrap(bytes).getInt()));
        else if (bytes.length == Long.BYTES) info.put(label, String.valueOf(ByteBuffer.wrap(bytes).getLong()));
    }
}
