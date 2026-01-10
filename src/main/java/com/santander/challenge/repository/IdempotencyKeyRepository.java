package com.santander.challenge.repository;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import com.santander.challenge.model.IdempotencyKey;

public interface IdempotencyKeyRepository extends JpaRepository<IdempotencyKey, Long> {
    Optional<IdempotencyKey> findByKeyValue(String keyValue);
}