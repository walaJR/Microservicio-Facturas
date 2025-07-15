package com.tienda.microservicio.config;

import java.util.Map;

import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.DirectExchange;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.rabbit.annotation.EnableRabbit;
import org.springframework.amqp.rabbit.connection.CachingConnectionFactory;
import org.springframework.amqp.rabbit.core.RabbitAdmin;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.amqp.support.converter.Jackson2JsonMessageConverter;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.event.EventListener;


@Configuration
@EnableRabbit
public class RabbitMQConfig {

    public static final String MAIN_QUEUE = "myQueue";
    public static final String DLX_QUEUE = "dlx-queue";
    public static final String MAIN_EXCHANGE = "myExchange";
    public static final String DLX_EXCHANGE = "dlx-exchange";
    public static final String ROUTING_KEY = "compra.routing.key";
    public static final String DLX_ROUTING_KEY = "dlx-routing-key";

    @Autowired
    private RabbitAdmin rabbitAdmin;

    @Bean
    Jackson2JsonMessageConverter messageConverter() {

        return new Jackson2JsonMessageConverter();
    }

    @Bean
    CachingConnectionFactory connectionFactory() {

        CachingConnectionFactory factory = new CachingConnectionFactory();
        factory.setHost("54.85.59.177");
        factory.setPort(5672);
        factory.setUsername("guest");
        factory.setPassword("guest");
        return factory;
    }

    @Bean
    public RabbitAdmin rabbitAdmin(CachingConnectionFactory connectionFactory) {
        return new RabbitAdmin(connectionFactory);
    }

    @Bean
    public RabbitTemplate rabbitTemplate(CachingConnectionFactory connectionFactory,
            Jackson2JsonMessageConverter messageConverter) {
        RabbitTemplate template = new RabbitTemplate(connectionFactory);
        template.setMessageConverter(messageConverter);
        return template;
    }

    @Bean
    Queue myQueue() {

        return new Queue(MAIN_QUEUE, true, false, false,
                Map.of("x-dead-letter-exchange", DLX_EXCHANGE, "x-dead-letter-routing-key", DLX_ROUTING_KEY));
    }

    @Bean
    Queue dlxQueue() {

        return new Queue(DLX_QUEUE);
    }

    @Bean
    DirectExchange myExchange() {

        return new DirectExchange(MAIN_EXCHANGE);
    }

    @Bean
    DirectExchange dlxExchange() {

        return new DirectExchange(DLX_EXCHANGE);
    }

    @Bean
    Binding binding(Queue myQueue, DirectExchange myExchange) {
        return BindingBuilder.bind(myQueue).to(myExchange).with(ROUTING_KEY);

    }

    @Bean
    Binding dlxBinding() {

        return BindingBuilder.bind(dlxQueue()).to(dlxExchange()).with(DLX_ROUTING_KEY);
    }

    @EventListener(ApplicationReadyEvent.class)
    public void inicializarRabbitMQ() {
        try {
            System.out.println("=== Iniciando configuración manual de RabbitMQ ===");

            // Forzar la declaración de todos los beans
            rabbitAdmin.initialize();

            System.out.println("RabbitMQ: Colas, exchanges y bindings creados correctamente");
            System.out.println("Cola principal: " + MAIN_QUEUE);
            System.out.println("Cola DLX: " + DLX_QUEUE);
            System.out.println("Exchange principal: " + MAIN_EXCHANGE);
            System.out.println("Exchange DLX: " + DLX_EXCHANGE);

        } catch (Exception e) {
            System.err.println("Error al inicializar RabbitMQ: " + e.getMessage());
            e.printStackTrace();
        }
    }

}
