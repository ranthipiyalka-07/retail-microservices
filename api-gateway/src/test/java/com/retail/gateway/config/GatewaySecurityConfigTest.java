
package com.retail.gateway.config;

import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.reactive.WebFluxTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpMethod;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.jwt.BadJwtException;
import org.springframework.security.oauth2.jwt.ReactiveJwtDecoder;
import org.springframework.security.test.web.reactive.server.SecurityMockServerConfigurers;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.reactive.server.WebTestClient;
import org.springframework.web.reactive.function.server.RouterFunction;
import org.springframework.web.reactive.function.server.ServerResponse;

import static org.springframework.web.reactive.function.server.RequestPredicates.all;
import static org.springframework.web.reactive.function.server.RouterFunctions.route;

@WebFluxTest
@Import({
        GatewaySecurityConfig.class,
        GatewaySecurityConfigTest.ProbeRoutes.class
})
class GatewaySecurityConfigTest {

    @TestConfiguration(proxyBeanMethods = false)
    static class ProbeRoutes {

        @Bean
        RouterFunction<ServerResponse> probeRouter() {
            return route(
                    all(),
                    request -> ServerResponse.ok().bodyValue("reached")
            );
        }
    }

    @Autowired
    WebTestClient client;

    @MockitoBean
    ReactiveJwtDecoder jwtDecoder;

    private WebTestClient.ResponseSpec anonymous(
            HttpMethod method, String path) {

        return client.method(method)
                .uri(path)
                .exchange();
    }

    private WebTestClient.ResponseSpec withRole(
            String role, HttpMethod method, String path) {

        return client.mutateWith(
                        SecurityMockServerConfigurers.mockJwt()
                                .authorities(
                                        new SimpleGrantedAuthority("ROLE_" + role)))
                .method(method)
                .uri(path)
                .exchange();
    }

    @Test
    void publicPathsNeedNoToken() {

        anonymous(HttpMethod.POST, "/api/v1/auth/login")
                .expectStatus().isOk();

        anonymous(HttpMethod.GET, "/api/v1/auth/.well-known/jwks.json")
                .expectStatus().isOk();

        anonymous(HttpMethod.POST, "/api/v1/customers/register")
                .expectStatus().isOk();

        anonymous(HttpMethod.GET, "/api/v1/products/1")
                .expectStatus().isOk();

        anonymous(HttpMethod.GET, "/api/v1/products/1/reviews")
                .expectStatus().isOk();

        anonymous(HttpMethod.GET, "/api/v1/categories")
                .expectStatus().isOk();

        anonymous(HttpMethod.GET, "/api/v1/campaigns/active")
                .expectStatus().isOk();

        anonymous(HttpMethod.GET, "/actuator/health")
                .expectStatus().isOk();

        anonymous(HttpMethod.OPTIONS, "/api/v1/orders")
                .expectStatus().isOk();
    }

    @Test
    void guestsCanUseTheCartButNeedLoginToMerge() {

        anonymous(HttpMethod.GET, "/api/v1/cart")
                .expectStatus().isOk();

        anonymous(HttpMethod.POST, "/api/v1/cart/items")
                .expectStatus().isOk();

        anonymous(HttpMethod.POST, "/api/v1/cart/merge")
                .expectStatus().isUnauthorized();

        withRole("CUSTOMER", HttpMethod.POST, "/api/v1/cart/merge")
                .expectStatus().isOk();
    }

    @Test
    void protectedPathsNeedAToken() {

        anonymous(HttpMethod.GET, "/api/v1/orders")
                .expectStatus().isUnauthorized();

        anonymous(HttpMethod.GET, "/api/v1/wishlist")
                .expectStatus().isUnauthorized();

        anonymous(HttpMethod.POST, "/api/v1/products")
                .expectStatus().isUnauthorized();

        anonymous(HttpMethod.GET, "/api/v1/products/1/reviews/mine")
                .expectStatus().isUnauthorized();

        anonymous(HttpMethod.GET, "/api/v1/support/tickets")
                .expectStatus().isUnauthorized();

        withRole("CUSTOMER", HttpMethod.GET, "/api/v1/orders")
                .expectStatus().isOk();

        withRole("CUSTOMER", HttpMethod.GET, "/api/v1/products/1/reviews/mine")
                .expectStatus().isOk();
    }

    @Test
    void internalPathsAreNeverReachable() {

        anonymous(HttpMethod.POST, "/internal/inventory/reservations")
                .expectStatus().isUnauthorized();

        withRole("ADMIN", HttpMethod.POST, "/internal/inventory/reservations")
                .expectStatus().isForbidden();
    }

    @Test
    void actuatorDetailsAreForAdminsOnly() {

        anonymous(HttpMethod.GET, "/actuator/gateway/routes")
                .expectStatus().isUnauthorized();

        withRole("CUSTOMER", HttpMethod.GET, "/actuator/gateway/routes")
                .expectStatus().isForbidden();

        withRole("ADMIN", HttpMethod.GET, "/actuator/gateway/routes")
                .expectStatus().isOk();
    }

    @Test
    void invalidTokenIsRejectedEvenOnPublicPath() {

        Mockito.when(jwtDecoder.decode("bad"))
                .thenReturn(
                        reactor.core.publisher.Mono.error(
                                new BadJwtException("bad token")
                        )
                );

        client.get()
                .uri("/api/v1/products/1")
                .header("Authorization", "Bearer bad")
                .exchange()
                .expectStatus().isUnauthorized();
    }

    @Test
    void unknownPathsAreDenied() {

        anonymous(HttpMethod.GET, "/something-else")
                .expectStatus().isUnauthorized();

        withRole("ADMIN", HttpMethod.GET, "/something-else")
                .expectStatus().isForbidden();
    }
}
