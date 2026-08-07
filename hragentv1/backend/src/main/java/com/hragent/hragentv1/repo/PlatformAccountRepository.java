package com.hragent.hragentv1.repo;

import com.hragent.hragentv1.domain.PlatformAccount;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface PlatformAccountRepository extends JpaRepository<PlatformAccount, Long> {
    Optional<PlatformAccount> findByUsernameIgnoreCase(String username);

    Optional<PlatformAccount> findByEmailIgnoreCase(String email);

    Optional<PlatformAccount> findByPublicId(String publicId);
}
