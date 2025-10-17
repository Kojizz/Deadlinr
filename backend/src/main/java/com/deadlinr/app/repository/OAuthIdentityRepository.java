package com.deadlinr.app.repository;

import com.deadlinr.app.entity.OAuthIdentity;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface OAuthIdentityRepository extends JpaRepository<OAuthIdentity, Long> {
    @EntityGraph(attributePaths = "user")
    Optional<OAuthIdentity> findByProviderAndSubject(String provider, String subject);
    Optional<OAuthIdentity> findByUserIdAndProvider(Long userId, String provider);
    List<OAuthIdentity> findByUserId(Long userId);
    long countByUserId(Long userId);
}
