package com.retail.gateway.config;

import io.micrometer.observation.ObservationPredicate;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.server.reactive.observation.ServerRequestObservationContext;

@Configuration
public class GatewayObservationConfig {

    /** Health checks and gateway diagnostics are not worth a trace. */
    @Bean
    public ObservationPredicate actuatorIsNotObserved() {
        return (name, context) -> !(context instanceof ServerRequestObservationContext request
                && request.getCarrier() != null
                && request.getCarrier().getPath().value().startsWith("/actuator"));
    }
}