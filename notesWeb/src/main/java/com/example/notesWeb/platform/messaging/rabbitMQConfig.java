package com.example.notesWeb.platform.messaging;

import org.springframework.amqp.core.*;
import org.springframework.amqp.rabbit.config.SimpleRabbitListenerContainerFactory;
import org.springframework.boot.autoconfigure.amqp.SimpleRabbitListenerContainerFactoryConfigurer;
import org.springframework.amqp.rabbit.connection.ConnectionFactory;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.amqp.support.converter.Jackson2JsonMessageConverter;
import org.springframework.amqp.support.converter.MessageConverter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class rabbitMQConfig {
    //Exchanges
    @Bean
    public DirectExchange remindDelayExchange() {
        return new DirectExchange("reminder.delay.exchange");
    }

    @Bean
    public DirectExchange remindMgExchange() {
        return new DirectExchange("reminder.mg.exchange");
    }

    //Queues
    @Bean
    public Queue remindDelayQueue() {
        return QueueBuilder.durable("reminder.delay.queue")
                .withArgument("x-dead-letter-exchange", "reminder.mg.exchange")
                .withArgument("x-dead-letter-routing-key", "reminder.ready.key")
                .build();
    }

    @Bean
    public Queue remindeMgQueue() {
        return QueueBuilder.durable("reminder.mg.queue").build();
    }

    //Bindings
    @Bean
    public Binding delayBinding() {
        return BindingBuilder.bind(remindDelayQueue())
                .to(remindDelayExchange())
                .with("reminder.delay.key");
    }

    @Bean
    public Binding mgBinding() {
        return BindingBuilder.bind(remindeMgQueue())
                .to(remindMgExchange())
                .with("reminder.ready.key");
    }

    @Bean
    public Declarables reliableQueues() {
        DirectExchange exchange = new DirectExchange("notes.events.v2", true, false);
        Queue ready = QueueBuilder.durable("notes.reminder.v2").quorum().withArgument("x-delivery-limit", -1).build();
        Queue dead = QueueBuilder.durable("notes.events.dlq").quorum().build();
        return new Declarables(exchange, ready, dead,
                BindingBuilder.bind(ready).to(exchange).with("reminder.ready"),
                BindingBuilder.bind(dead).to(exchange).with("dead"));
    }

    @Bean
    public Declarables noteUpdateQueue() {
        TopicExchange exchange=new TopicExchange("notes.note-updates.v1",true,false);
        Queue queue=QueueBuilder.durable("notes.note-updates.v1").quorum()
                .withArgument("x-delivery-limit",-1).withArgument("x-single-active-consumer",true).build();
        return new Declarables(exchange,queue,BindingBuilder.bind(queue).to(exchange).with("#"));
    }

    @Bean
    public SimpleRabbitListenerContainerFactory noteUpdateListenerFactory(
            SimpleRabbitListenerContainerFactoryConfigurer configurer,
            ConnectionFactory connectionFactory) {
        var factory=new SimpleRabbitListenerContainerFactory();
        configurer.configure(factory,connectionFactory);
        // shortcut: one active consumer limits throughput; partition queues when measured load requires it.
        factory.setPrefetchCount(1);
        return factory;
    }

    // JSON converter
    @Bean
    public MessageConverter jacksonMessageConverter() {
        return new Jackson2JsonMessageConverter();
    }

    @Bean
    public RabbitTemplate rabbitTemplate(
            ConnectionFactory connectionFactory,
            MessageConverter jacksonMessageConverter
    ) {
        RabbitTemplate template = new RabbitTemplate(connectionFactory);
        template.setMessageConverter(jacksonMessageConverter);
        if (connectionFactory instanceof org.springframework.amqp.rabbit.connection.CachingConnectionFactory cached) {
            cached.setPublisherConfirmType(org.springframework.amqp.rabbit.connection.CachingConnectionFactory.ConfirmType.CORRELATED);
            cached.setPublisherReturns(true);
        }
        template.setMandatory(true);
        return template;
    }
}
