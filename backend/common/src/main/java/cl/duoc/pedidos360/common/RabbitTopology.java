package cl.duoc.pedidos360.common;

import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.DirectExchange;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.core.QueueBuilder;
import org.springframework.amqp.core.TopicExchange;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Single source of truth for the RabbitMQ contract shared by the services.
 * Queue, exchange, binding and routing-key names must be added here before
 * being used by a publisher or consumer.
 */
@Configuration
public class RabbitTopology {
    public static final String DIRECT_EXCHANGE = "cmd.direct";
    public static final String TOPIC_EXCHANGE = "cmd.topic";
    public static final String DEAD_LETTER_EXCHANGE = "cmd.dead.dlx";

    public static final String EMAIL_QUEUE = "q.cmd.email";
    public static final String KITCHEN_QUEUE = "q.cmd.kitchen";
    public static final String INVOICE_QUEUE = "q.cmd.invoice";
    public static final String EMAIL_DLQ = EMAIL_QUEUE + ".dlq";
    public static final String KITCHEN_DLQ = KITCHEN_QUEUE + ".dlq";
    public static final String INVOICE_DLQ = INVOICE_QUEUE + ".dlq";

    public static final String EMAIL_ROUTING_KEY = "email.send";
    public static final String KITCHEN_ROUTING_KEY = "kitchen.ticket";
    public static final String INVOICE_ROUTING_KEY = "invoice.gen";

    @Bean
    public DirectExchange commandDirectExchange() {
        return new DirectExchange(DIRECT_EXCHANGE, true, false);
    }

    @Bean
    public TopicExchange commandTopicExchange() {
        return new TopicExchange(TOPIC_EXCHANGE, true, false);
    }

    @Bean
    public DirectExchange commandDeadLetterExchange() {
        return new DirectExchange(DEAD_LETTER_EXCHANGE, true, false);
    }

    @Bean
    public Queue emailQueue(@Value("${app.rabbit-quorum-size:1}") int quorumSize) {
        return commandQueue(EMAIL_QUEUE, EMAIL_ROUTING_KEY, quorumSize);
    }

    @Bean
    public Queue kitchenQueue(@Value("${app.rabbit-quorum-size:1}") int quorumSize) {
        return commandQueue(KITCHEN_QUEUE, KITCHEN_ROUTING_KEY, quorumSize);
    }

    @Bean
    public Queue invoiceQueue(@Value("${app.rabbit-quorum-size:1}") int quorumSize) {
        return commandQueue(INVOICE_QUEUE, INVOICE_ROUTING_KEY, quorumSize);
    }

    @Bean
    public Queue emailDeadLetterQueue(@Value("${app.rabbit-quorum-size:1}") int quorumSize) {
        return deadLetterQueue(EMAIL_DLQ, quorumSize);
    }

    @Bean
    public Queue kitchenDeadLetterQueue(@Value("${app.rabbit-quorum-size:1}") int quorumSize) {
        return deadLetterQueue(KITCHEN_DLQ, quorumSize);
    }

    @Bean
    public Queue invoiceDeadLetterQueue(@Value("${app.rabbit-quorum-size:1}") int quorumSize) {
        return deadLetterQueue(INVOICE_DLQ, quorumSize);
    }

    @Bean
    public Binding emailDirectBinding(@Qualifier("emailQueue") Queue emailQueue,
                                     @Qualifier("commandDirectExchange") DirectExchange commandDirectExchange) {
        return BindingBuilder.bind(emailQueue).to(commandDirectExchange).with(EMAIL_ROUTING_KEY);
    }

    @Bean
    public Binding kitchenDirectBinding(@Qualifier("kitchenQueue") Queue kitchenQueue,
                                        @Qualifier("commandDirectExchange") DirectExchange commandDirectExchange) {
        return BindingBuilder.bind(kitchenQueue).to(commandDirectExchange).with(KITCHEN_ROUTING_KEY);
    }

    @Bean
    public Binding invoiceDirectBinding(@Qualifier("invoiceQueue") Queue invoiceQueue,
                                        @Qualifier("commandDirectExchange") DirectExchange commandDirectExchange) {
        return BindingBuilder.bind(invoiceQueue).to(commandDirectExchange).with(INVOICE_ROUTING_KEY);
    }

    @Bean
    public Binding emailTopicBinding(@Qualifier("emailQueue") Queue emailQueue,
                                     @Qualifier("commandTopicExchange") TopicExchange commandTopicExchange) {
        return BindingBuilder.bind(emailQueue).to(commandTopicExchange).with("email.#");
    }

    @Bean
    public Binding kitchenTopicBinding(@Qualifier("kitchenQueue") Queue kitchenQueue,
                                       @Qualifier("commandTopicExchange") TopicExchange commandTopicExchange) {
        return BindingBuilder.bind(kitchenQueue).to(commandTopicExchange).with("kitchen.#");
    }

    @Bean
    public Binding invoiceTopicBinding(@Qualifier("invoiceQueue") Queue invoiceQueue,
                                       @Qualifier("commandTopicExchange") TopicExchange commandTopicExchange) {
        return BindingBuilder.bind(invoiceQueue).to(commandTopicExchange).with("invoice.#");
    }

    @Bean
    public Binding emailDeadLetterBinding(@Qualifier("emailDeadLetterQueue") Queue emailDeadLetterQueue,
                                         @Qualifier("commandDeadLetterExchange") DirectExchange commandDeadLetterExchange) {
        return BindingBuilder.bind(emailDeadLetterQueue).to(commandDeadLetterExchange).with(EMAIL_ROUTING_KEY);
    }

    @Bean
    public Binding kitchenDeadLetterBinding(@Qualifier("kitchenDeadLetterQueue") Queue kitchenDeadLetterQueue,
                                            @Qualifier("commandDeadLetterExchange") DirectExchange commandDeadLetterExchange) {
        return BindingBuilder.bind(kitchenDeadLetterQueue).to(commandDeadLetterExchange).with(KITCHEN_ROUTING_KEY);
    }

    @Bean
    public Binding invoiceDeadLetterBinding(@Qualifier("invoiceDeadLetterQueue") Queue invoiceDeadLetterQueue,
                                            @Qualifier("commandDeadLetterExchange") DirectExchange commandDeadLetterExchange) {
        return BindingBuilder.bind(invoiceDeadLetterQueue).to(commandDeadLetterExchange).with(INVOICE_ROUTING_KEY);
    }

    private static Queue commandQueue(String name, String routingKey, int quorumSize) {
        return QueueBuilder.durable(name)
                .quorum()
                .withArgument("x-quorum-initial-group-size", quorumSize)
                .deadLetterExchange(DEAD_LETTER_EXCHANGE)
                .deadLetterRoutingKey(routingKey)
                .build();
    }

    private static Queue deadLetterQueue(String name, int quorumSize) {
        return QueueBuilder.durable(name)
                .quorum()
                .withArgument("x-quorum-initial-group-size", quorumSize)
                .build();
    }
}
