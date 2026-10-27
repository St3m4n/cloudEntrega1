package cl.duoc.pedidos360.common;
import org.springframework.context.annotation.*;
import org.springframework.amqp.core.*;
import org.springframework.beans.factory.annotation.Value;
import java.util.*;
@Configuration
public class RabbitTopology {
    @Bean Declarables topology(@Value("${app.rabbit-quorum-size:1}") int quorumSize) {
        var all=new ArrayList<Declarable>();
        var direct=new DirectExchange("cmd.direct",true,false);var topic=new TopicExchange("cmd.topic",true,false);var dead=new DirectExchange("cmd.dead.dlx",true,false);all.addAll(List.of(direct,topic,dead));
        for(var entry:Map.of("email","email.send","kitchen","kitchen.ticket","invoice","invoice.gen").entrySet()) {
            String name="q.cmd."+entry.getKey(),key=entry.getValue();
            var queue=QueueBuilder.durable(name).quorum().withArgument("x-quorum-initial-group-size",quorumSize).deadLetterExchange("cmd.dead.dlx").deadLetterRoutingKey(key).build();
            var dlq=QueueBuilder.durable(name+".dlq").quorum().withArgument("x-quorum-initial-group-size",quorumSize).build();
            all.addAll(List.of(queue,dlq,BindingBuilder.bind(queue).to(direct).with(key),BindingBuilder.bind(queue).to(topic).with(entry.getKey()+".#"),BindingBuilder.bind(dlq).to(dead).with(key)));
        }
        return new Declarables(all);
    }
}
