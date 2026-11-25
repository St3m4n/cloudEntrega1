package cl.duoc.pedidos360.audit;

import cl.duoc.pedidos360.common.kafka.KafkaTopics;
import java.nio.charset.StandardCharsets;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.KafkaHeaders;
import org.springframework.stereotype.Component;

/** Lee el DLT de audit como texto (puede traer JSON inválido) y deja un log con el motivo del rechazo. */
@Component
public class AuditDeadLetterListener {
    private static final Logger log = LoggerFactory.getLogger(AuditDeadLetterListener.class);

    @KafkaListener(topics = KafkaTopics.AUDIT_TIMELINE_DLT, groupId = "${app.kafka.group-id}-dlt",
            properties = "value.deserializer=org.apache.kafka.common.serialization.StringDeserializer")
    public void onDeadLetter(ConsumerRecord<String, String> record) {
        log.error("Mensaje en DLT topic={} partition={} offset={} key={} origen={} error={}", record.topic(), record.partition(),
                record.offset(), record.key(), header(record, KafkaHeaders.DLT_ORIGINAL_TOPIC), header(record, KafkaHeaders.DLT_EXCEPTION_MESSAGE));
    }

    private static String header(ConsumerRecord<?, ?> record, String name) {
        var header = record.headers().lastHeader(name);
        return header == null ? "-" : new String(header.value(), StandardCharsets.UTF_8);
    }
}
