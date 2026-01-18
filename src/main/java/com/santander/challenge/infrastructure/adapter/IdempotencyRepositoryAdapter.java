package com.santander.challenge.infrastructure.adapter;

import com.santander.challenge.domain.port.IdempotencyRepositoryPort;
import com.santander.challenge.infrastructure.persistence.entity.IdempotencyKeyEntity;
import com.santander.challenge.infrastructure.redis.service.RedisIdempotencyCacheService;
import com.santander.challenge.infrastructure.repository.IdempotencyKeyJpaRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.Optional;

/**
 * Adaptador del repositorio de idempotencia - Clean Architecture
 * Implementa el puerto del dominio usando JPA + Redis Cache (Cache-Aside Pattern)
 *
 * Estrategia:
 * - Read: Redis primero (cache), luego H2 si miss
 * - Write: H2 + Redis simultáneamente
 * - Fallback: Si Redis falla, usar solo H2
 */
@Component
public class IdempotencyRepositoryAdapter implements IdempotencyRepositoryPort {

    private static final Logger logger = LoggerFactory.getLogger(IdempotencyRepositoryAdapter.class);

    private final IdempotencyKeyJpaRepository jpaRepository;
    private final RedisIdempotencyCacheService cacheService;

    public IdempotencyRepositoryAdapter(
            IdempotencyKeyJpaRepository jpaRepository,
            RedisIdempotencyCacheService cacheService) {
        this.jpaRepository = jpaRepository;
        this.cacheService = cacheService;
    }

    @Override
    public boolean saveKey(String key) {
        // Write-through: Escribir en BD primero (source of truth)
        if (jpaRepository.existsById(key)) {
            logger.debug("Clave de idempotencia ya existe en BD: {}", key);
            // Asegurar que está en cache también
            cacheService.cacheKey(key);
            return false;
        }

        // Guardar en BD
        jpaRepository.save(new IdempotencyKeyEntity(key));
        logger.debug("Clave de idempotencia guardada en BD: {}", key);

        // Cachear en Redis (no bloqueante)
        cacheService.cacheKey(key);

        return true;
    }

    @Override
    public boolean existsKey(String key) {
        // Cache-Aside: Verificar cache primero
        Optional<Boolean> cachedResult = cacheService.existsInCache(key);

        if (cachedResult.isPresent()) {
            // Cache HIT - retorno directo (ultra-rápido)
            return cachedResult.get();
        }

        // Cache MISS - verificar en BD (source of truth)
        boolean existsInDb = jpaRepository.existsById(key);

        if (existsInDb) {
            // Encontrado en BD - cachear para próximas consultas
            logger.debug("Clave encontrada en BD, cacheando: {}", key);
            cacheService.cacheKey(key);
        }

        return existsInDb;
    }

    @Override
    public Optional<String> findKey(String key) {
        return jpaRepository.findById(key)
                .map(IdempotencyKeyEntity::getKey);
    }

    @Override
    public void deleteKey(String key) {
        // Eliminar de BD
        jpaRepository.deleteById(key);
        logger.debug("Clave de idempotencia eliminada de BD: {}", key);

        // Invalidar cache
        cacheService.invalidateKey(key);
    }
}
