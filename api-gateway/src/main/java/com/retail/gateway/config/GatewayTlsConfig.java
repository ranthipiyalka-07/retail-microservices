package com.retail.gateway.config;

import io.netty.handler.ssl.SslContext;
import io.netty.handler.ssl.SslContextBuilder;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.ssl.SslBundles;
import org.springframework.boot.ssl.SslManagerBundle;
import org.springframework.cloud.gateway.config.HttpClientCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.http.client.reactive.ReactorClientHttpConnector;
import org.springframework.security.oauth2.jwt.NimbusReactiveJwtDecoder;
import org.springframework.security.oauth2.jwt.ReactiveJwtDecoder;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.netty.http.client.HttpClient;

import javax.net.ssl.SSLEngine;
import javax.net.ssl.SSLException;
import javax.net.ssl.SSLParameters;
import java.time.Duration;

/** Active only when the gateway itself runs on HTTPS (server.ssl.bundle is set). */
@Configuration
@ConditionalOnProperty(name = "server.ssl.bundle")
public class GatewayTlsConfig {

    private final SslContext clientContext;

    public GatewayTlsConfig(SslBundles bundles, @Value("${server.ssl.bundle}") String bundleName) {
        SslManagerBundle managers = bundles.getBundle(bundleName).getManagers();
        try {
            // our certificate (so the services accept us) and our CA (so we accept them)
            this.clientContext = SslContextBuilder.forClient()
                    .keyManager(managers.getKeyManagerFactory())
                    .trustManager(managers.getTrustManagerFactory())
                    .build();
        } catch (SSLException e) {
            throw new IllegalStateException("Cannot create the gateway's TLS client context", e);
        }
    }

    /** Every route to an https:// backend uses it. Plain http:// backends are not affected. */
    @Bean
    public HttpClientCustomizer mutualTlsHttpClient() {
        return this::secure;
    }

    /** The default decoder has its own HTTP client without a client certificate: auth-service would refuse it. */
    @Bean
    @Primary
    public ReactiveJwtDecoder jwtDecoder(@Value("${spring.security.oauth2.resourceserver.jwt.jwk-set-uri}") String jwkSetUri) {
        HttpClient http = secure(HttpClient.create().responseTimeout(Duration.ofSeconds(5)));
        WebClient webClient = WebClient.builder().clientConnector(new ReactorClientHttpConnector(http)).build();
        return NimbusReactiveJwtDecoder.withJwkSetUri(jwkSetUri).webClient(webClient).build();
    }

    private HttpClient secure(HttpClient client) {
        return client.secure(spec -> spec.sslContext(clientContext).handlerConfigurator(handler -> {
            SSLEngine engine = handler.engine();
            SSLParameters parameters = engine.getSSLParameters();
            parameters.setEndpointIdentificationAlgorithm("HTTPS");     // the certificate must match the host name
            engine.setSSLParameters(parameters);
        }));
    }
}