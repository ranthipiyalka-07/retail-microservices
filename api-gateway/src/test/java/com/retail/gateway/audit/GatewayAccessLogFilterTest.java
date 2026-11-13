package com.retail.gateway.audit;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;
import org.springframework.web.server.WebFilterChain;

import java.net.InetSocketAddress;
import java.util.HashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class GatewayAccessLogFilterTest {

    private final Logger logger = (Logger) LoggerFactory.getLogger("AUDIT");
    private final GatewayAccessLogFilter filter = new GatewayAccessLogFilter();
    private ListAppender<ILoggingEvent> appender;

    @BeforeEach
    void attach() {
        appender = new ListAppender<>();
        appender.start();
        logger.addAppender(appender);
        logger.setLevel(Level.INFO);
    }

    @AfterEach
    void detach() {
        logger.detachAppender(appender);
    }

    private static WebFilterChain answering(HttpStatus status) {
        return exchange -> {
            exchange.getResponse().setStatusCode(status);
            return exchange.getResponse().setComplete();
        };
    }

    private void run(MockServerHttpRequest request, HttpStatus status) {
        filter.filter(MockServerWebExchange.from(request), answering(status)).block();
    }

    private Map<String, Object> fields(ILoggingEvent event) {
        Map<String, Object> map = new HashMap<>();
        event.getKeyValuePairs().forEach(pair -> map.put(pair.key, pair.value));
        return map;
    }

    @Test
    void aRejectedRequestWithoutATokenIsLoggedAsAFailure() {
        run(MockServerHttpRequest.get("/api/v1/orders").remoteAddress(new InetSocketAddress("203.0.113.7", 5555)).build(),
                HttpStatus.UNAUTHORIZED);

        assertThat(appender.list).hasSize(1);
        assertThat(appender.list.get(0).getLevel()).isEqualTo(Level.WARN);
        assertThat(fields(appender.list.get(0))).containsEntry("auditAction", "http.request")
                .containsEntry("auditOutcome", "FAILURE").containsEntry("auditActorType", "anonymous")
                .containsEntry("httpStatus", 401).containsEntry("httpPath", "/api/v1/orders")
                .containsEntry("clientIp", "203.0.113.7");
    }

    @Test
    void aSuccessfulCallWithATokenIsLoggedAndMarkedBearer() {
        run(MockServerHttpRequest.post("/api/v1/orders").header("Authorization", "Bearer secret-token").build(), HttpStatus.CREATED);

        assertThat(appender.list).hasSize(1);
        Map<String, Object> f = fields(appender.list.get(0));
        assertThat(f).containsEntry("auditOutcome", "SUCCESS").containsEntry("auditActorType", "bearer");
        assertThat(f.values()).noneMatch(value -> String.valueOf(value).contains("secret-token"));
    }

    @Test
    void anonymousReadsActuatorAndPreflightAreNotLogged() {
        run(MockServerHttpRequest.get("/api/v1/products/1").build(), HttpStatus.OK);
        run(MockServerHttpRequest.get("/actuator/health").build(), HttpStatus.OK);
        run(MockServerHttpRequest.options("/api/v1/orders").build(), HttpStatus.OK);

        assertThat(appender.list).isEmpty();
    }
}