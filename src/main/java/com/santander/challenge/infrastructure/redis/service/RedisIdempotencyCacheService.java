package com.santander.challenge.infrastructure.redis.service;

import java.time.Duration;
import java.util.Optional;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

/**
 * Servicio de caché Redis para claves de idempotencia.
 *
 * Implementa patrón Cache-Aside:
 * - Read: Primero Redis, luego BD si miss
 * - Write: Escribir en BD y Redis simultáneamente
 *
 * Ventajas:
 * - ⚡ Verificaciones ultra-rápidas (<1ms vs ~10ms de BD)
 * - 📉 Reduce carga en base de datos
 * - 🔄 Distribuido entre instancias
 * - 💾 BD como source of truth (fallback)
 *
 * Key Pattern: "idempotency:{key}"
 */
@Service
public class RedisIdempotencyCacheService {

    private static final Logger logger = LoggerFactory.getLogger(RedisIdempotencyCacheService.class);

    private static final String IDEMPOTENCY_KEY_PREFIX = "idempotency:";
    private static final String CACHED_VALUE = "true";

    private final RedisTemplate<String, String> stringRedisTemplate;

    @Value("${app.redis.idempotency.ttl:604800}") // 7 días por defecto
    private long idempotencyTtlSeconds;

    public RedisIdempotencyCacheService(RedisTemplate<String, String> stringRedisTemplate) {
        this.stringRedisTemplate = stringRedisTemplate;
    }

    /**
     * Verifica si una clave existe en el caché.
     *
     * @param key La clave de idempotencia
     * @return Optional.empty() si no está en caché (cache miss),
     *         Optional.of(true) si existe en caché
     */
    public Optional<Boolean> existsInCache(String key) {
        try {
            String redisKey = IDEMPOTENCY_KEY_PREFIX + key;
            Boolean exists = stringRedisTemplate.hasKey(redisKey);

            if (Boolean.TRUE.equals(exists)) {
                logger.debug("Cache HIT para idempotency key: {}", key);
                return Optional.of(true);
            } else {
                logger.debug("Cache MISS para idempotency key: {}", key);
                return Optional.empty();
            }

        } catch (Exception e) {
            logger.warn("Error verificando Redis cache, fallback a BD: {}", e.getMessage());
            return Optional.empty(); // En caso de error, fallback a BD
        }
    }

    /**
     * Guarda una clave en el caché.
     *
     * @param key La clave de idempotencia a cachear
     */
    public void cacheKey(String key) {
        try {
            String redisKey = IDEMPOTENCY_KEY_PREFIX + key;

            stringRedisTemplate.opsForValue().set(
                redisKey,
                CACHED_VALUE,
                Duration.ofSeconds(idempotencyTtlSeconds)
            );

            logger.debug("Clave de idempotencia cacheada: {}, TTL: {}s", key, idempotencyTtlSeconds);

        } catch (Exception e) {
            logger.warn("Error cacheando clave en Redis (no crítico): {}", e.getMessage());
            // No lanzamos excepción - el cache es complementario
        }
    }

    /**
     * Invalida una clave del caché.
     * Útil para testing o cleanup manual.
     *
     * @param key La clave de idempotencia a invalidar
     */
    public void invalidateKey(String key) {
        try {
            String redisKey = IDEMPOTENCY_KEY_PREFIX + key;
            stringRedisTemplate.delete(redisKey);

            logger.debug("Clave de idempotencia invalidada del cache: {}", key);

        } catch (Exception e) {
            logger.warn("Error invalidando clave en Redis: {}", e.getMessage());
        }
    }

    /**
     * Invalida todas las claves de idempotencia del caché.
     * ⚠️ Usar con precaución - puede impactar performance temporalmente.
     */
    public void invalidateAll() {
        try {
            var keys = stringRedisTemplate.keys(IDEMPOTENCY_KEY_PREFIX + "*");

            if (keys != null && !keys.isEmpty()) {
                stringRedisTemplate.delete(keys);
                logger.info("Cache de idempotencia completamente invalidado. Keys eliminadas: {}", keys.size());
            }

        } catch (Exception e) {
            logger.error("Error invalidando todo el cache de idempotencia: {}", e.getMessage());
        }
    }

    /**
     * Obtiene estadísticas del caché.
     *
     * @return Número de claves de idempotencia en caché
     */
    public long getCacheSize() {
        try {
            var keys = stringRedisTemplate.keys(IDEMPOTENCY_KEY_PREFIX + "*");
            return keys != null ? keys.size() : 0;

        } catch (Exception e) {
            logger.error("Error obteniendo tamaño del cache: {}", e.getMessage());
            return 0;
        }
    }
}
