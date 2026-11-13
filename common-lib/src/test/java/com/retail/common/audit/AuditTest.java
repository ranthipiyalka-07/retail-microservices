package com.retail.common.audit;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.authentication.TestingAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class AuditTest {

    private final Logger logger = (Logger) LoggerFactory.getLogger("AUDIT");
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

    private ILoggingEvent only() {
        assertThat(appender.list).hasSize(1);
        return appender.list.get(0);
    }

    @Test
    void aLoggedInUserIsTheActorWithHisRoles() {
        SecurityContextHolder.getContext().setAuthentication(
                new TestingAuthenticationToken("admin@retail.local", "n/a", "ROLE_ADMIN", "ROLE_STORE_MANAGER"));

        Audit.record("product.create", Audit.Outcome.SUCCESS, "product", 12L, "sku=TEE-001");

        ILoggingEvent event = only();
        Map<String, Object> f = fields(event);
        assertThat(event.getLevel()).isEqualTo(Level.INFO);
        assertThat(f).containsEntry("auditAction", "product.create").containsEntry("auditOutcome", "SUCCESS")
                .containsEntry("auditActorType", "user").containsEntry("auditActor", "admin@retail.local")
                .containsEntry("auditActorRoles", "ADMIN,STORE_MANAGER").containsEntry("auditEntity", "product")
                .containsEntry("auditEntityId", "12").containsEntry("auditDetail", "sku=TEE-001");
    }

    @Test
    void anotherServiceCallingWithTheInternalKeyIsAService() {
        SecurityContextHolder.getContext().setAuthentication(
                new TestingAuthenticationToken("internal-service", "n/a", "ROLE_INTERNAL"));

        Audit.record("payment.refund", Audit.Outcome.SUCCESS, "payment", 3L, null);

        assertThat(fields(only())).containsEntry("auditActorType", "service").containsEntry("auditActor", "internal-service")
                .doesNotContainKey("auditDetail");
    }

    @Test
    void withoutALoginTheActorIsTheSystemOrAnAnonymousVisitor() {
        Audit.record("order.reconcile", Audit.Outcome.SUCCESS, "order", 1L, null);
        SecurityContextHolder.getContext().setAuthentication(new AnonymousAuthenticationToken(
                "key", "anonymousUser", List.of(new SimpleGrantedAuthority("ROLE_ANONYMOUS"))));
        Audit.record("x", Audit.Outcome.SUCCESS, null, null, null);

        assertThat(fields(appender.list.get(0))).containsEntry("auditActorType", "system");
        assertThat(fields(appender.list.get(1))).containsEntry("auditActorType", "anonymous");
    }

    @Test
    void aFailureIsAWarningAndAnExplicitActorIsKept() {
        Audit.recordAs(Audit.Actor.user("john@example.com"), "auth.login", Audit.Outcome.FAILURE, "user", null, "wrong password");

        ILoggingEvent event = only();
        assertThat(event.getLevel()).isEqualTo(Level.WARN);
        assertThat(fields(event)).containsEntry("auditActor", "john@example.com").containsEntry("auditOutcome", "FAILURE")
                .doesNotContainKey("auditEntityId");
    }

    @Test
    void textIsCleanedSoNobodyCanForgeALogLineAndLongTextIsCut() {
        Audit.record("order.cancel", Audit.Outcome.SUCCESS, "order", 7L, "line1\nINFO fake line\r\n" + "x".repeat(1000));

        String detail = (String) fields(only()).get("auditDetail");
        assertThat(detail).doesNotContain("\n").doesNotContain("\r").hasSizeLessThanOrEqualTo(300);
        assertThat(detail).startsWith("line1 INFO fake line");
    }
}