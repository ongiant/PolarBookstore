package com.polarbookshop.catalogservice;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.polarbookshop.catalogservice.domain.Book;
import dasniko.testcontainers.keycloak.KeycloakContainer;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.reactive.server.WebTestClient;
import org.springframework.web.reactive.function.BodyInserters;
import org.springframework.web.reactive.function.client.WebClient;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("integration")
@Testcontainers
class CatalogServiceApplicationTests {

    private static KeycloakToken isabelleTokens;
    private static KeycloakToken bjornTokens;

    @Autowired
    private WebTestClient webTestClient;

    @Container
    private static final KeycloakContainer keycloakContainer = new KeycloakContainer("quay.io/keycloak/keycloak:23.0")
        .withRealmImportFile("test-realm-config.json");

    @DynamicPropertySource
    static void dynamicProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.security.oauth2.resourceserver.jwt.issuer-uri",
            () -> keycloakContainer.getAuthServerUrl() + "/realms/PolarBookshop");
    }

    @BeforeAll
    static void generateAccessTokens() {
        // why use reactive client for imperative catalog-service?
        WebClient webClient = WebClient.builder()
            // url refer official docs: https://www.keycloak.org/docs/latest/securing_apps/#token-endpoint
            .baseUrl(keycloakContainer.getAuthServerUrl() + "/realms/PolarBookshop/protocol/openid-connect/token")
            // about header: https://datatracker.ietf.org/doc/html/rfc6749#section-4.3.2
            .defaultHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_FORM_URLENCODED_VALUE)
            .build();

        isabelleTokens = authenticateWith("isabelle", "password", webClient);
        bjornTokens = authenticateWith("bjorn", "password", webClient);
    }

    private static KeycloakToken authenticateWith(String username, String password, WebClient webClient) {
        return webClient
            .post()
            // body setup is referred this official doc link:
            //   1.  https://datatracker.ietf.org/doc/html/rfc6749#section-4.3.2
            //   2.  https://www.keycloak.org/docs/latest/securing_apps/#example-using-curl
            //   3.  https://www.keycloak.org/docs/latest/securing_apps/#how-token-exchange-works
            // 'curl -d' command will use application/x-www-form-urlencoded content-type automatically, This achieves the same effect as the BodyInserters used in the code.
            .body(BodyInserters.fromFormData("grant_type", "password")
                .with("client_id", "polar-test")
                .with("username", username)
                .with("password", password)
            )
            .retrieve()
            /**
             * GPT4:
             * the bodyToMono(KeycloakToken.class) invocation instructs the WebClient to convert the HTTP response body into a Mono publisher that will emit an item of type KeycloakToken.
             * The @JsonCreator and @JsonProperty annotations are ways for Jackson (the JSON library used by Spring's WebClient by default) to handle nuances during deserialization
             *   from JSON to the Java object (KeycloakToken) especially when there are discrepancies between field names in the JSON and the object field.
             * In this case, @JsonProperty("access_token") handles the discrepancy between the JSON's access_token field and Java's accessToken field.
             * In a reactive programming model, like the one Spring's WebClient adheres to, the actual work (in this case, sending the HTTP request, receiving the response,
             *   and deserializing it into a KeycloakToken) doesn't happen until something subscribes to the Mono<KeycloakToken>.
             * When bodyToMono(KeycloakToken.class) is called, it doesn't immediately deserialize the HTTP response. Instead, it sets up the "recipe" for the work to be done later.
             * That "later" is when something (like your application code) subscribes to the Mono<KeycloakToken> returned by the authenticateWith method.
             * At that point, WebClient sends the HTTP request, receives the response, and deserializes it into a KeycloakToken.
             */
            .bodyToMono(KeycloakToken.class)
            .block();
    }

    private record KeycloakToken(String accessToken) {
        @JsonCreator
        // Keycloak returned JSON contains access token named with 'access_token',
        // for more details: https://www.keycloak.org/docs/latest/authorization_services/index.html#_service_protection_whatis_obtain_pat
        private KeycloakToken(@JsonProperty("access_token") final String accessToken) {
            this.accessToken = accessToken;
        }
    }

    @Test
    void whenGetRequestWithIdThenBookReturned() {
        var bookIsbn = "1231231230";
        var bookToCreate = Book.of(bookIsbn, "Title", "Author", 9.90, "Polarsophia");
        Book expectedBook = webTestClient
            .post()
            .uri("/books")
            .headers(headers -> headers.setBearerAuth(isabelleTokens.accessToken()))
            .bodyValue(bookToCreate)
            .exchange()
            .expectStatus().isCreated()
            .expectBody(Book.class).value(book -> assertThat(book).isNotNull())
            .returnResult().getResponseBody();

        webTestClient
            .get()
            .uri("/books/" + bookIsbn)
            .exchange()
            .expectStatus().is2xxSuccessful()
            .expectBody(Book.class).value(actualBook -> {
                assertThat(actualBook).isNotNull();
                assertThat(actualBook.isbn()).isEqualTo(expectedBook.isbn());
            });
    }

    @Test
    void whenPostRequestThenBookCreated() {
        var expectedBook = Book.of("1231231231", "Title", "Author", 9.90, "Polarsophia");

        webTestClient
            .post()
            .uri("/books")
            .headers(headers -> headers.setBearerAuth(isabelleTokens.accessToken()))
            .bodyValue(expectedBook)
            .exchange()
            .expectStatus().isCreated()
            .expectBody(Book.class).value(actualBook -> {
                assertThat(actualBook).isNotNull();
                assertThat(actualBook.isbn()).isEqualTo(expectedBook.isbn());
            });
    }

    @Test
    void whenPutRequestThenBookUpdated() {
        var bookIsbn = "1231231232";
        var bookToCreate = Book.of(bookIsbn, "Title", "Author", 9.90, "Polarsophia");
        Book createdBook = webTestClient
            .post()
            .uri("/books")
            .headers(headers -> headers.setBearerAuth(isabelleTokens.accessToken()))
            .bodyValue(bookToCreate)
            .exchange()
            .expectStatus().isCreated()
            .expectBody(Book.class).value(book -> assertThat(book).isNotNull())
            .returnResult().getResponseBody();
        var bookToUpdate = new Book(createdBook.id(), createdBook.isbn(), createdBook.title(), createdBook.author(), 7.95,
            createdBook.publisher(), createdBook.createdDate(), createdBook.lastModifiedDate(), createdBook.version());

        webTestClient
            .put()
            .uri("/books/" + bookIsbn)
            .headers(headers -> headers.setBearerAuth(isabelleTokens.accessToken()))
            .bodyValue(bookToUpdate)
            .exchange()
            .expectStatus().isOk()
            .expectBody(Book.class).value(actualBook -> {
                assertThat(actualBook).isNotNull();
                assertThat(actualBook.price()).isEqualTo(bookToUpdate.price());
            });
    }

    @Test
    void whenDeleteRequestThenBookDeleted() {
        var bookIsbn = "1231231233";
        var bookToCreate = Book.of(bookIsbn, "Title", "Author", 9.90, "Polarsophia");
        webTestClient
            .post()
            .uri("/books")
            .headers(headers -> headers.setBearerAuth(isabelleTokens.accessToken()))
            .bodyValue(bookToCreate)
            .exchange()
            .expectStatus().isCreated();

        webTestClient
            .delete()
            .uri("/books/" + bookIsbn)
            .headers(headers -> headers.setBearerAuth(isabelleTokens.accessToken()))
            .exchange()
            .expectStatus().isNoContent();

        webTestClient
            .get()
            .uri("/books/" + bookIsbn)
            .exchange()
            .expectStatus().isNotFound()
            .expectBody(String.class).value(errorMessage ->
                assertThat(errorMessage).isEqualTo("The book with ISBN " + bookIsbn + " was not found.")
            );
    }

    @Test
    void whenPostRequestUnauthorizedThen403() {
        var expectedBook = Book.of("1231231231", "Title", "Author", 9.90, "Polarsophia");

        webTestClient.post().uri("/books")
            .headers(headers -> headers.setBearerAuth(bjornTokens.accessToken()))
            .bodyValue(expectedBook)
            .exchange()
            .expectStatus().isForbidden();
    }

    @Test
    void whenPostRequestUnauthenticatedThen401() {
        var expectedBook = Book.of("1231231231", "Title", "Author", 9.90, "Polarsophia");

        webTestClient.post().uri("/books")
            .bodyValue(expectedBook)
            .exchange()
            .expectStatus().isUnauthorized();
    }
}
