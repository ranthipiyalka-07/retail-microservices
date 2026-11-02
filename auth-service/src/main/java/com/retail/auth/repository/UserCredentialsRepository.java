package com.retail.auth.repository;

import com.retail.auth.domain.UserCredentials;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface UserCredentialsRepository extends JpaRepository<UserCredentials,Long> {
    Optional<UserCredentials> findByEmail(String email);
    boolean existsByEmail(String email);
    boolean existsByCustomerId(Long customerId);
}
