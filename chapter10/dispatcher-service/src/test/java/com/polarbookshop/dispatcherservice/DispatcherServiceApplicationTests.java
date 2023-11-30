package com.polarbookshop.dispatcherservice;

import org.junit.jupiter.api.Test;
import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.rabbit.core.RabbitAdmin;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.RabbitMQContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

/**
 * to implement this integration test using real RabbitMQ broker, I referred 2 docs:
 * 1. https://www.youtube.com/watch?v=9jZInwFtp44&ab_channel=SivaLabs
 * 2. https://stackoverflow.com/questions/49816044/connect-to-message-broker-with-spring-cloud-stream-from-test
 */

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Testcontainers
class DispatcherServiceApplicationTests {

    @Container
    static RabbitMQContainer rabbitMQ = new RabbitMQContainer(DockerImageName.parse("rabbitmq:3.10-management"));

    /**
     * about DynamicPropertySource annotation, reference document:
     * 1. https://docs.spring.io/spring-boot/docs/2.7.18/reference/html/howto.html#howto.testing.testcontainers
     * 2. https://docs.spring.io/spring-framework/reference/testing/testcontext-framework/ctx-management/dynamic-property-sources.html
      */

    @DynamicPropertySource
    static void rabbitMQProperties(DynamicPropertyRegistry registry){
        registry.add("spring.rabbitmq.host", rabbitMQ::getHost);
        registry.add("spring.rabbitmq.port", rabbitMQ::getAmqpPort);
        registry.add("spring.rabbitmq.username", rabbitMQ::getAdminUsername);
        registry.add("spring.rabbitmq.password", rabbitMQ::getAdminPassword);
    }

    /**
     * @SpringBootTest annotation will auto help you to add beans(e.g. RabbitTemplate, RabbitAdmin etc.)
     * for more details, see: https://docs.spring.io/spring-amqp/docs/current/reference/html/#spring-rabbit-test
      */

    @Autowired
    private RabbitTemplate rabbitTemplate;

    @Autowired
    private RabbitAdmin rabbitAdmin; // you can use AmqpAdmin class too

    @Test
    void contextLoads() {
    }

    @Test
    void packAndLabel(){
        long orderId = 121;

        // 1. config broker, more see doc:
        Queue inputQueue = this.rabbitAdmin.declareQueue();
        assert inputQueue != null;
        Binding inputBinding = new Binding(inputQueue.getName(), Binding.DestinationType.QUEUE, "order-accepted", "dispatcher-service", null);

        Queue outputQueue = this.rabbitAdmin.declareQueue();
        assert outputQueue != null;
        Binding outputBinding = new Binding(outputQueue.getName(), Binding.DestinationType.QUEUE, "order-dispatched", "#", null);

        this.rabbitAdmin.declareBinding(inputBinding);
        this.rabbitAdmin.declareBinding(outputBinding);

        rabbitTemplate.convertAndSend("order-accepted", "dispatcher-service", new OrderAcceptedMessage(orderId));


        await().atMost(5, TimeUnit.SECONDS).untilAsserted(() -> {
            OrderDispatchedMessage message = rabbitTemplate.receiveAndConvert(outputQueue.getName(),
                10000, new ParameterizedTypeReference<OrderDispatchedMessage>(){});

            assert message != null;
            assertThat(message.orderId()).isEqualTo(orderId);
            System.out.println("------------------------------------: " + message.orderId());
        });
    }
}
