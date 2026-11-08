package com.retail.auth.service;

import ch.qos.logback.core.util.StringUtil;
import com.retail.auth.config.AuthProperties;
import com.retail.auth.domain.AccountStatus;
import com.retail.auth.domain.Role;
import com.retail.auth.domain.UserCredentials;
import com.retail.auth.repository.RoleRepository;
import com.retail.auth.repository.UserCredentialsRepository;
import jakarta.transaction.Transactional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.util.Locale;

/** Dev convenience: creates one ADMIN account at startup if retail.auth.bootstrap-admin is configured. */
@Component
public class AdminBootstrap implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(AdminBootstrap.class);

    private final UserCredentialsRepository users;
    private final RoleRepository roles;
    private final PasswordEncoder passwordEncoder;
    private final AuthProperties props;

    public AdminBootstrap(UserCredentialsRepository users, RoleRepository roles,
                          PasswordEncoder passwordEncoder, AuthProperties props) {
        this.users = users;
        this.roles = roles;
        this.passwordEncoder = passwordEncoder;
        this.props = props;
    }

    @Override
    @Transactional
    public void run(ApplicationArguments args) throws Exception {
        AuthProperties.BootstrapAdmin admin =props.bootstrapAdmin();

        if(admin == null || !StringUtils.hasText((admin.email())) || !StringUtils.hasText(admin.password())) {
            log.info("No bootstrap admin configured");
            return;
        }

        String email = admin.email().trim().toLowerCase(Locale.ROOT);
        if(users.existsByEmail(email)) {
            log.info("Admin user already exists: {}", email);
            return;
        }

        Role adminRole = roles.findByName("ADMIN").orElseThrow(() -> new IllegalStateException("Role ADMIN is missing"));

        UserCredentials user = new UserCredentials();
        user.setEmail(email);
        user.setPasswordHash(passwordEncoder.encode(admin.password()));
        user.setStatus(AccountStatus.ACTIVE);
        user.getRoles().add(adminRole);
        users.save(user);
        log.warn("Bootstrap admin '{}' created. Remove retail.auth.bootstrap-admin outside development.", email);
    }
}
