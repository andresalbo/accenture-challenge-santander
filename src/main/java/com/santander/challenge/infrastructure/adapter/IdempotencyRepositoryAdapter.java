package com.santander.challenge.infrastructure.adapter;

import com.santander.challenge.domain.port.IdempotencyRepositoryPort;
import com.santander.challenge.infrastructure.persistence.entity.IdempotencyKeyEntity;
import com.santander.challenge.infrastructure.repository.IdempotencyKeyJpaRepository;
import org.springframework.stereotype.Component;

import java.util.Optional;

/**
 * Adaptador del repositorio de idempotencia - Clean Architecture
 * Implementa el puerto del dominio usando JPA
 */
@Component
public class IdempotencyRepositoryAdapter implements IdempotencyRepositoryPort {

    private final IdempotencyKeyJpaRepository jpaRepository;

    public IdempotencyRepositoryAdapter(IdempotencyKeyJpaRepository jpaRepository) {
        this.jpaRepository = jpaRepository;
    }

    @Override
    public boolean saveKey(String key) {
        if (jpaRepository.existsById(key)) {
            return false;
        }
        jpaRepository.save(new IdempotencyKeyEntity(key));
        return true;
    }

    @Override
    public boolean existsKey(String key) {
        return jpaRepository.existsById(key);
    }

    @Override
    public Optional<String> findKey(String key) {
        return jpaRepository.findById(key)
                .map(IdempotencyKeyEntity::getKey);
    }

    @Override
    public void deleteKey(String key) {
        jpaRepository.deleteById(key);
    }
}
