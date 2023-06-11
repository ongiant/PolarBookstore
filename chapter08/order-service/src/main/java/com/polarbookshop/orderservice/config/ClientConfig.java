package com.polarbookshop.orderservice.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.reactive.function.client.WebClient;

@Configuration
public class ClientConfig {

    @Bean
    // spring-boot auto-configuration WebClient.Builder as webClientBuilder bean, for more reference: https://github.com/spring-projects/spring-boot/blob/v2.7.9/spring-boot-project/spring-boot-autoconfigure/src/main/java/org/springframework/boot/autoconfigure/web/reactive/function/client/WebClientAutoConfiguration.java
    WebClient webClient(ClientProperties clientProperties, WebClient.Builder webClientBuilder){
        return webClientBuilder.baseUrl(clientProperties.catalogServiceUri().toString()).build();
    }
}
