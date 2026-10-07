package cl.duoc.pedidos360.common;
import org.springframework.context.annotation.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.listener.*;
import org.springframework.util.backoff.FixedBackOff;
import org.apache.kafka.common.TopicPartition;
@Configuration
public class StreamingConfig {
    @Bean DefaultErrorHandler kafkaErrorHandler(KafkaTemplate<String,String> template,@Value("${spring.application.name}") String name) {
        var recoverer=new DeadLetterPublishingRecoverer(template,(record,error)->new TopicPartition(record.topic()+"."+name+".DLT",record.partition()));
        recoverer.setFailIfSendResultIsError(true);
        return new DefaultErrorHandler(recoverer,new FixedBackOff(1000L,3L));
    }
}
