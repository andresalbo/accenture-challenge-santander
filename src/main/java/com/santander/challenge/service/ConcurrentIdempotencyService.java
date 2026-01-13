package com.santander.challenge.service;

import java.time.Duration;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.locks.ReentrantLock;
import java.util.concurrent.TimeUnit;

import org.springframework.stereotype.Service;

/**
 * Servicio de idempotencia thread-safe usando características de Java 21.
 * Maneja la concurrencia con locks explícitos para prevenir race conditions.
 *
 * Características:
 * - Thread-safe usando ConcurrentHashMap y ReentrantLock
 * - Previene race conditions en inserts concurrentes
 * - Compatible con Virtual Threads de Java 21
 * - Limpieza automática de locks antiguos
 */
@Service
public class ConcurrentIdempotencyService {

    // Map de locks por idempotency key para serializar peticiones con el mismo key
    private final ConcurrentHashMap<String, ReentrantLock> lockMap = new ConcurrentHashMap<>();

    // Map de keys procesados (cache en memoria)
    private final ConcurrentHashMap<String, ProcessingResult> processedKeys = new ConcurrentHashMap<>();

    // Timeout para adquisición de lock (previene deadlocks)
    private static final long LOCK_TIMEOUT_SECONDS = 10;

    /**
     * Resultado de procesamiento de una key
     */
    public record ProcessingResult(
        boolean success,
        String message,
        long timestamp
    ) {}

    /**
     * Intenta procesar una petición con idempotencia garantizada.
     * Usa locks por key para serializar peticiones con el mismo idempotency-key.
     *
     * @param idempotencyKey El key único de la petición
     * @param processor La lógica de negocio a ejecutar (solo se ejecuta si no está duplicada)
     * @return ProcessingResult con el resultado del procesamiento
     */
    public ProcessingResult processWithIdempotency(
            String idempotencyKey,
            IdempotentProcessor processor) {

        // Obtener o crear un lock específico para este idempotency-key
        ReentrantLock lock = lockMap.computeIfAbsent(
            idempotencyKey,
            k -> new ReentrantLock(true) // fair=true para FIFO
        );

        try {
            // Intentar adquirir el lock con timeout
            boolean acquired = lock.tryLock(LOCK_TIMEOUT_SECONDS, TimeUnit.SECONDS);

            if (!acquired) {
                return new ProcessingResult(
                    false,
                    "Request timeout - could not acquire lock",
                    System.currentTimeMillis()
                );
            }

            try {
                // Verificar si ya fue procesado (double-check dentro del lock)
                ProcessingResult cached = processedKeys.get(idempotencyKey);
                if (cached != null) {
                    return cached; // Retornar resultado cacheado
                }

                // Ejecutar la lógica de negocio
                ProcessingResult result;
                try {
                    result = processor.process();
                } catch (Exception e) {
                    // Capturar excepciones de la lógica de negocio
                    return new ProcessingResult(
                        false,
                        "Error during processing: " + e.getMessage(),
                        System.currentTimeMillis()
                    );
                }

                // Cachear el resultado si fue exitoso
                if (result.success()) {
                    processedKeys.put(idempotencyKey, result);
                }

                return result;

            } finally {
                lock.unlock();
            }

        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return new ProcessingResult(
                false,
                "Request interrupted",
                System.currentTimeMillis()
            );
        } finally {
            // Limpieza: remover lock si no hay threads esperando
            cleanupLockIfUnused(idempotencyKey, lock);
        }
    }

    /**
     * Verifica si un key ya fue procesado (sin bloquear)
     */
    public boolean isAlreadyProcessed(String idempotencyKey) {
        return processedKeys.containsKey(idempotencyKey);
    }

    /**
     * Limpia el lock del map si no está siendo usado
     */
    private void cleanupLockIfUnused(String key, ReentrantLock lock) {
        // Solo remover si el lock no está siendo usado y no hay threads esperando
        if (!lock.hasQueuedThreads() && !lock.isLocked()) {
            lockMap.remove(key, lock);
        }
    }

    /**
     * Limpia los resultados procesados más antiguos que la duración especificada.
     * Útil para evitar crecimiento infinito del cache.
     */
    public void cleanupOldResults(Duration maxAge) {
        long cutoffTime = System.currentTimeMillis() - maxAge.toMillis();
        processedKeys.entrySet().removeIf(
            entry -> entry.getValue().timestamp() < cutoffTime
        );
    }

    /**
     * Obtiene estadísticas del servicio (útil para monitoreo)
     */
    public Stats getStats() {
        return new Stats(
            processedKeys.size(),
            lockMap.size()
        );
    }

    public record Stats(int processedKeysCount, int activeLocks) {}

    /**
     * Interface funcional para la lógica de negocio
     */
    @FunctionalInterface
    public interface IdempotentProcessor {
        ProcessingResult process() throws Exception;
    }
}

