package com.retail.payment.config;

import com.retail.payment.security.InternalApiKeyFilter;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

@Configuration
@EnableWebSecurity
public class SecurityConfig {

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http,
                                           @Value("${retail.internal.api-key}") String internalKey) throws Exception {
        http
                .csrf(AbstractHttpConfigurer::disable)
                .httpBasic(AbstractHttpConfigurer::disable)
                .formLogin(AbstractHttpConfigurer::disable)
                .sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(a -> a
                        .requestMatchers("/actuator/health", "/actuator/info").permitAll()
                        .requestMatchers("/internal/**").hasRole("INTERNAL")
                        .anyRequest().denyAll())
                .addFilterBefore(new InternalApiKeyFilter(internalKey), UsernamePasswordAuthenticationFilter.class);
        return http.build();
    }

    /** No form login here, so no default user (avoids the "generated security password" log line). */
    @Bean
    public UserDetailsService userDetailsService() {
        return username -> { throw new UsernameNotFoundException(username); };
    }
}