package com.schedulix.auth;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface EmailTokenRepository extends JpaRepository<EmailToken, UUID> {

    Optional<EmailToken> findByTokenHashAndPurpose(String tokenHash, TokenPurpose purpose);

    List<EmailToken> findByUserAccountIdAndPurposeAndUsedAtIsNull(UUID userAccountId, TokenPurpose purpose);
}