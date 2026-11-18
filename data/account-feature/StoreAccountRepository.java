package com.dudububu.store;

import org.springframework.data.jpa.repository.JpaRepository;
import java.util.Optional;
import java.util.UUID;

interface StoreAccountRepository extends JpaRepository<StoreAccount, UUID> {
    Optional<StoreAccount> findByEmail(String email);
}
