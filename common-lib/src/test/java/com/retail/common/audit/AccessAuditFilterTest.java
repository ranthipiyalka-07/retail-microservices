package com.retail.common.audit;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletResponse;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.authentication.TestingAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class AccessAuditFilterTest {

    private final Logger logger = (Logger) LoggerFactory.getLogger("AUDIT");
    private final AccessAuditFilter filter = new AccessAuditFilter();
    private ListAppender<ILoggingEvent> appender;

    @BeforeEach
    void attach() {
        SecurityContextHolder.clearContext();
        appender = new ListAppender<>();
        appender.start();
        logger.addAppender(appender);
        logger.setLevel(Level.INFO);
    }

    @AfterEach
    void detach() {
        logger.detachAppender(appender);
        SecurityContextHolder.clearContext();
    }

    private Map<String, Object> fields(ILoggingEvent event) {
        Map<String, Object> map = new HashMap<>();
        event.getKeyValuePairs().forEach(pair -> map.put(pair.key, pair.value));
        return map;
    }

    private void loginAs(String email) {
        SecurityContextHolder.getContext().setAuthentication(new TestingAuthenticationToken(email, "n/a", "ROLE_CUSTOMER"));
    }

    private void run(MockHttpServletRequest request, int status) throws Exception {
        FilterChain chain = (req, res) -> ((HttpServletResponse) res).setStatus(status);
        filter.doFilter(request, new MockHttpServletResponse(), chain);
    }

    @Test
    void anAuthenticatedRequestIsLoggedWithUserPathStatusAndDuration() throws Exception {
        loginAs("john@example.com");

        run(new MockHttpServletRequest("POST", "/api/v1/orders"), 201);

        assertThat(appender.list).hasSize(1);
        Map<String, Object> f = fields(appender.list.get(0));
        assertThat(appender.list.get(0).getLevel()).isEqualTo(Level.INFO);
        assertThat(f).containsEntry("auditAction", "http.request").containsEntry("auditOutcome", "SUCCESS")
                .containsEntry("auditActor", "john@example.com").containsEntry("httpMethod", "POST")
                .containsEntry("httpPath", "/api/v1/orders").containsEntry("httpStatus", 201);
        assertThat((Long) f.get("durationMs")).isGreaterThanOrEqualTo(0L);
    }

    @Test
    void anonymousSuccessfulReadsAndServiceToServiceReadsAreNotLogged() throws Exception {
        run(new MockHttpServletRequest("GET", "/api/v1/products/1"), 200);
        SecurityContextHolder.getContext().setAuthentication(new TestingAuthenticationToken("internal-service", "n/a", "ROLE_INTERNAL"));
        run(new MockHttpServletRequest("GET", "/internal/products/summaries"), 200);

        assertThat(appender.list).isEmpty();
    }

    @Test
    void failuresAreAlwaysLoggedEvenForAnonymousVisitors() throws Exception {
        SecurityContextHolder.getContext().setAuthentication(new AnonymousAuthenticationToken(
                "key", "anonymousUser", List.of(new SimpleGrantedAuthority("ROLE_ANONYMOUS"))));

        run(new MockHttpServletRequest("GET", "/api/v1/orders/9"), 404);

        assertThat(appender.list).hasSize(1);
        assertThat(appender.list.get(0).getLevel()).isEqualTo(Level.WARN);
        assertThat(fields(appender.list.get(0))).containsEntry("auditOutcome", "FAILURE").containsEntry("httpStatus", 404);
    }

    @Test
    void actuatorAndPreflightRequestsAreSkipped() throws Exception {
        loginAs("john@example.com");

        run(new MockHttpServletRequest("GET", "/actuator/health"), 200);
        run(new MockHttpServletRequest("OPTIONS", "/api/v1/orders"), 200);

        assertThat(appender.list).isEmpty();
    }

    @Test
    void theForwardedAddressIsUsedOnlyWhenItLooksLikeAnAddress() throws Exception {
        loginAs("john@example.com");
        MockHttpServletRequest good = new MockHttpServletRequest("POST", "/api/v1/cart/items");
        good.addHeader("X-Forwarded-For", "203.0.113.7, 10.0.0.2");
        MockHttpServletRequest bad = new MockHttpServletRequest("POST", "/api/v1/cart/items");
        bad.addHeader("X-Forwarded-For", "evil\ninjected");
        bad.setRemoteAddr("10.9.8.7");

        run(good, 200);
        run(bad, 200);

        assertThat(fields(appender.list.get(0))).containsEntry("clientIp", "203.0.113.7");
        assertThat(fields(appender.list.get(1))).containsEntry("clientIp", "10.9.8.7");
    }

    @Test
    void aFailingRequestIsLoggedAsAServerErrorAndTheExceptionIsRethrown() {
        loginAs("john@example.com");
        FilterChain failing = (req, res) -> { throw new ServletException("boom"); };

        assertThatThrownBy(() -> filter.doFilter(new MockHttpServletRequest("POST", "/api/v1/orders"),
                new MockHttpServletResponse(), failing)).isInstanceOf(ServletException.class);

        assertThat(appender.list).hasSize(1);
        assertThat(fields(appender.list.get(0))).containsEntry("httpStatus", 500).containsEntry("auditOutcome", "FAILURE");
    }
}