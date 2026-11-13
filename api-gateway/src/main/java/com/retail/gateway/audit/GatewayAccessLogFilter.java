package com.retail.gateway.audit;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.spi.LoggingEventBuilder;
import org.springframework.core.Ordered;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatusCode;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.web.server.WebFilter;
import org.springframework.web.server.WebFilterChain;
import reactor.core.publisher.Mono;

import java.net.InetSocketAddress;

/**
 * Logs every external request (the gateway has no logged-in user at this point: it only knows whether a token was sent).
 * Order: behind Boot's observation filter (so the traceId is known), in front of Spring Security (so 401 and 403 are seen).
 */
@Component
public class GatewayAccessLogFilter implements WebFilter, Ordered {

    private static final Logger LOG = LoggerFactory.getLogger("AUDIT");

    @Override
    public int getOrder() {
        return Ordered.HIGHEST_PRECEDENCE + 2;
    }

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, WebFilterChain chain) {
        String path = exchange.getRequest().getPath().value();
        if (path.startsWith("/actuator") || HttpMethod.OPTIONS.equals(exchange.getRequest().getMethod())) {
            return chain.filter(exchange);
        }
        long start = System.nanoTime();
        exchange.getResponse().beforeCommit(() -> {             // the status is final here, whoever set it
            try {
                log(exchange, path, (System.nanoTime() - start) / 1_000_000L);
            } catch (RuntimeException ignored) {
                // an audit problem must never break a request
            }
            return Mono.empty();
        });
        return chain.filter(exchange);
    }

    private void log(ServerWebExchange exchange, String path, long durationMs) {
        HttpStatusCode statusCode = exchange.getResponse().getStatusCode();
        int status = statusCode == null ? 200 : statusCode.value();
        String method = String.valueOf(exchange.getRequest().getMethod());
        boolean hasToken = exchange.getRequest().getHeaders().getFirst(HttpHeaders.AUTHORIZATION) != null;
        boolean readOnly = "GET".equals(method) || "HEAD".equals(method);
        if (readOnly && status < 400 && !hasToken) {
            return;                                             // anonymous catalog browsing is not audited
        }

        LoggingEventBuilder event = status >= 400 ? LOG.atWarn() : LOG.atInfo();
        event.setMessage("AUDIT http.request " + method + " " + status)
                .addKeyValue("auditAction", "http.request")
                .addKeyValue("auditOutcome", status < 400 ? "SUCCESS" : "FAILURE")
                .addKeyValue("auditActorType", hasToken ? "bearer" : "anonymous")
                .addKeyValue("httpMethod", clean(method, 10))
                .addKeyValue("httpPath", clean(path, 300))
                .addKeyValue("httpStatus", status)
                .addKeyValue("durationMs", durationMs)
                .addKeyValue("clientIp", clientIp(exchange))
                .log();
    }

    /** Only the socket address: X-Forwarded-For is attacker-controlled at the edge. Behind a trusted load balancer, use its header. */
    private static String clientIp(ServerWebExchange exchange) {
        InetSocketAddress remote = exchange.getRequest().getRemoteAddress();
        return remote == null || remote.getAddress() == null ? "unknown" : remote.getAddress().getHostAddress();
    }

    private static String clean(String value, int max) {
        String cleaned = value.replaceAll("\\p{Cntrl}", " ").replaceAll("\\s+", " ").trim();
        return cleaned.length() > max ? cleaned.substring(0, max) : cleaned;
    }
}