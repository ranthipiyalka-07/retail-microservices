package com.retail.common.audit;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.spi.LoggingEventBuilder;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.regex.Pattern;

/**
 * Registered after Spring Security, so the authenticated user is known. Requests that Spring Security itself rejects
 * (401 and 403 at the filter chain) never get here: the gateway's access log covers those.
 */
public class AccessAuditFilter extends OncePerRequestFilter {

    private static final Logger LOG = LoggerFactory.getLogger("AUDIT");
    private static final Pattern IP = Pattern.compile("^[0-9a-fA-F:.]{3,45}$");

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        return request.getRequestURI().startsWith("/actuator") || "OPTIONS".equalsIgnoreCase(request.getMethod());
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        long start = System.nanoTime();
        int status = 500;                                             // stays 500 when the chain throws
        try {
            chain.doFilter(request, response);
            status = response.getStatus();
        } finally {
            try {
                log(request, status, (System.nanoTime() - start) / 1_000_000L);
            } catch (RuntimeException e) {
                logger.debug("Could not write the access audit line", e);   // an audit problem must never break a request
            }
        }
    }

    private void log(HttpServletRequest request, int status, long durationMs) {
        Audit.Actor actor = Audit.currentActor();
        String method = request.getMethod();
        boolean readOnly = "GET".equals(method) || "HEAD".equals(method);
        boolean quiet = !"user".equals(actor.type());                 // anonymous visitors, other services, background jobs
        if (readOnly && status < 400 && quiet) {
            return;                                                   // catalog browsing and service-to-service reads are not audited
        }

        LoggingEventBuilder event = status >= 400 ? LOG.atWarn() : LOG.atInfo();
        event = event.setMessage("AUDIT http.request " + method + " " + status)
                .addKeyValue("auditAction", "http.request")
                .addKeyValue("auditOutcome", status < 400 ? "SUCCESS" : "FAILURE")
                .addKeyValue("auditActorType", actor.type())
                .addKeyValue("auditActor", actor.id())
                .addKeyValue("httpMethod", Audit.clean(method, 10))
                .addKeyValue("httpPath", Audit.clean(request.getRequestURI(), 300))
                .addKeyValue("httpStatus", status)
                .addKeyValue("durationMs", durationMs)
                .addKeyValue("clientIp", clientIp(request));
        if (actor.roles() != null) {
            event = event.addKeyValue("auditActorRoles", actor.roles());
        }
        event.log();
    }

    /**
     * The gateway sets X-Forwarded-For, so its first entry is the caller. A service that can be reached directly can be
     * sent a fake header: the value is therefore only accepted when it looks like an address.
     */
    private static String clientIp(HttpServletRequest request) {
        String forwarded = request.getHeader("X-Forwarded-For");
        if (forwarded != null) {
            String first = forwarded.split(",")[0].trim();
            if (IP.matcher(first).matches()) {
                return first;
            }
        }
        String remote = request.getRemoteAddr();
        return remote == null ? "unknown" : Audit.clean(remote, 45);
    }
}