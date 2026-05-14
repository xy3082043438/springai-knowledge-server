package com.lamb.springaiknowledgeserver.core.config;

import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.DirectExchange;
import org.springframework.amqp.core.Queue;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class RabbitConfig {

    public static final String DOCUMENT_EXCHANGE = "document.exchange";
    public static final String DOCUMENT_PROCESS_QUEUE = "document.process.queue";
    public static final String DOCUMENT_ROUTING_KEY = "document.process.routing.key";

    @Bean
    public DirectExchange documentExchange() {
        return new DirectExchange(DOCUMENT_EXCHANGE);
    }

    @Bean
    public Queue documentProcessQueue() {
        return new Queue(DOCUMENT_PROCESS_QUEUE, true); // durable queue
    }

    @Bean
    public Binding documentProcessBinding() {
        return BindingBuilder.bind(documentProcessQueue())
                .to(documentExchange())
                .with(DOCUMENT_ROUTING_KEY);
    }
}
