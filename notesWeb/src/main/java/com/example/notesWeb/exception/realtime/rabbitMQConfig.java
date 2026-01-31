package com.example.notesWeb.exception.realtime;

import org.springframework.amqp.core.*;
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
        return template;
    }
}
