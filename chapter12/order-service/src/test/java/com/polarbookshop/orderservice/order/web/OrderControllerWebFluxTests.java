package com.polarbookshop.orderservice.order.web;

import com.polarbookshop.orderservice.config.SecurityConfig;
import com.polarbookshop.orderservice.order.domain.Order;
import com.polarbookshop.orderservice.order.domain.OrderService;
import com.polarbookshop.orderservice.order.domain.OrderStatus;
import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.Import;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.jwt.ReactiveJwtDecoder;
import org.springframework.security.test.web.reactive.server.SecurityMockServerConfigurers;
import reactor.core.publisher.Mono;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.reactive.WebFluxTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.web.reactive.server.WebTestClient;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;

@WebFluxTest(OrderController.class)
@Import(SecurityConfig.class)
class OrderControllerWebFluxTests {

    @Autowired
    private WebTestClient webClient;

    @MockBean
    private OrderService orderService;

    @MockBean
    private ReactiveJwtDecoder reactiveJwtDecoder;

    @Test
    void whenBookNotAvailableThenRejectOrder() {
        var orderRequest = new OrderRequest("1234567890", 3);
        var expectedOrder = OrderService.buildRejectedOrder(orderRequest.isbn(), orderRequest.quantity());
        given(orderService.submitOrder(orderRequest.isbn(), orderRequest.quantity()))
            .willReturn(Mono.just(expectedOrder));

        /**
         *
         * 1. for the mutateWith method part, see :for details: https://docs.spring.io/spring-security/reference/5.8/reactive/test/web/oauth2.html#webflux-testing-jwt
         * 2. GPT4 illustrated:
         *   1) The method mutateWith() from WebTestClient along with SecurityMockServerConfigurers.mockJwt() is setting up a mocked security context,
         *      which simulates a situation where the request is sent with an "Authorization" header carrying a JWT.
         *   2) SecurityMockServerConfigurers.mockJwt().authorities(new SimpleGrantedAuthority("ROLE_customer")) is used to create a mocked Security Context for the WebTestClient.
         *      This is to mimic an authenticated client with the authority ROLE_customer during the test.
         *   3) In this mock environment, the system will behave as if it received a JWT and verified it using a public key, when, in reality, it has not.
         *   4) In this test, the public key is not fetched or used. The point here is to bypass actual security mechanisms,
         *      thus focusing the test efforts on the controller logic, while simulating a security-authorized context.
         */
        webClient
            .mutateWith(SecurityMockServerConfigurers
                .mockJwt()
                .authorities(new SimpleGrantedAuthority("ROLE_customer"))
            )
            .post()
            .uri("/orders")
            .bodyValue(orderRequest)
            .exchange()
            .expectStatus().is2xxSuccessful()
            .expectBody(Order.class).value(actualOrder -> {
                assertThat(actualOrder).isNotNull();
                assertThat(actualOrder.status()).isEqualTo(OrderStatus.REJECTED);
            });

    }

}
