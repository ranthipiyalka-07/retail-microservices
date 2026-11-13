package com.retail.common.audit;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.spi.LoggingEventBuilder;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.LinkedHashSet;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Security-relevant events (BRD 5.5): logins, data changes, refunds. Written to the logger "AUDIT": the log pipeline
 * routes everything with an auditAction field to its own index with a long retention.
 * Never pass a password, token, card or other secret as detail.
 */
public final class Audit {

    private static final Logger LOG = LoggerFactory.getLogger("AUDIT");

    public enum Outcome { SUCCESS, FAILURE }

    public record Actor(String type, String id, String roles) {
        /** For events where nobody is logged in yet, such as a login attempt. */
        public static Actor user(String email) {
            return new Actor("user", clean(email, 255), null);
        }

        public static Actor system() {
            return new Actor("system", "system", null);
        }
    }

    private Audit() {}

    /** The actor is whoever is logged in on this thread: a user, another service (internal key), or "system" for background jobs. */
    public static void record(String action, Outcome outcome, String entity, Object entityId, String detail) {
        recordAs(currentActor(), action, outcome, entity, entityId, detail);
    }

    public static void recordAs(Actor actor, String action, Outcome outcome, String entity, Object entityId, String detail) {
        LoggingEventBuilder event = outcome == Outcome.FAILURE ? LOG.atWarn() : LOG.atInfo();
        event = event.setMessage("AUDIT " + clean(action, 100) + " " + outcome)
                .addKeyValue("auditAction", clean(action, 100))
                .addKeyValue("auditOutcome", outcome.name())
                .addKeyValue("auditActorType", actor.type())
                .addKeyValue("auditActor", actor.id());
        if (actor.roles() != null) {
            event = event.addKeyValue("auditActorRoles", actor.roles());
        }
        if (entity != null) {
            event = event.addKeyValue("auditEntity", clean(entity, 60));
        }
        if (entityId != null) {
            event = event.addKeyValue("auditEntityId", clean(String.valueOf(entityId), 64));
        }
        String text = clean(detail, 300);
        if (!text.isEmpty()) {
            event = event.addKeyValue("auditDetail", text);
        }
        event.log();
    }

    public static Actor currentActor() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null) {
            return Actor.system();                                    // a scheduled job or a Kafka consumer
        }
        if (auth instanceof AnonymousAuthenticationToken) {
            return new Actor("anonymous", "anonymous", null);
        }
        Set<String> roles = auth.getAuthorities().stream()
                .map(GrantedAuthority::getAuthority)
                .map(a -> a.startsWith("ROLE_") ? a.substring(5) : a)
                .sorted()
                .collect(Collectors.toCollection(LinkedHashSet::new));
        String type = roles.contains("INTERNAL") ? "service" : "user";       // the internal-key filter grants ROLE_INTERNAL
        return new Actor(type, clean(auth.getName(), 255), roles.isEmpty() ? null : clean(String.join(",", roles), 200));
    }

    /** One line, no control characters (nobody can forge a log line), cut to max. Never null. */
    static String clean(String value, int max) {
        if (value == null) {
            return "";
        }
        String cleaned = value.replaceAll("\\p{Cntrl}", " ").replaceAll("\\s+", " ").trim();
        return cleaned.length() > max ? cleaned.substring(0, max).trim() : cleaned;
    }
}