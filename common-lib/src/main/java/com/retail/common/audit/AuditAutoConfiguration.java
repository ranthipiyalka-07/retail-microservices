package com.retail.common.audit;

import io.micrometer.observation.ObservationPredicate;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.core.Ordered;
import org.springframework.http.server.observation.ServerRequestObservationContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;

@AutoConfiguration
@ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.SERVLET)
@ConditionalOnClass({OncePerRequestFilter.class, SecurityContextHolder.class})
public class AuditAutoConfiguration {

    /** Switch off with retail.audit.access.enabled=false. */
    @Bean
    @ConditionalOnProperty(prefix = "retail.audit.access", name = "enabled", havingValue = "true", matchIfMissing = true)
    public FilterRegistrationBean<AccessAuditFilter> accessAuditFilter() {
        FilterRegistrationBean<AccessAuditFilter> registration = new FilterRegistrationBean<>(new AccessAuditFilter());
        registration.setOrder(Ordered.LOWEST_PRECEDENCE - 100);      // far behind Spring Security (-100): the user is known
        return registration;
    }

    /** Health checks and metrics scrapes are not worth a trace. */
    @Bean
    public ObservationPredicate actuatorIsNotObserved() {
        return (name, context) -> !(context instanceof ServerRequestObservationContext request
                && request.getCarrier() != null
                && request.getCarrier().getRequestURI().startsWith("/actuator"));
    }
}