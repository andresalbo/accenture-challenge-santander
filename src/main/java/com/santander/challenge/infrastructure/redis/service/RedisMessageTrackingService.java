package com.santander.challenge.infrastructure.redis.service;

import java.time.Duration;
import java.util.Collection;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import com.santander.challenge.infrastructure.kafka.dto.EntidadBancariaMessage;

/**
 * Servicio de tracking de mensajes usando Redis como storage distribuido.
 *
 * Ventajas sobre ConcurrentHashMap:
 * - ✅ Distribuido (múltiples instancias de la app)
 * - ✅ Persistente (sobrevive a reinicios con AOF/RDB)
 * - ✅ TTL automático (no memory leaks)
 * - ✅ Escalable
 *
 * Key Patterns:
 * - message:id:{messageId} -> EntidadBancariaMessage (JSON)
 * - message:idempotency:{idempotencyKey} -> messageId (String)
 */
@Service
public class RedisMessageTrackingService {

    private static final Logger logger = LoggerFactory.getLogger(RedisMessageTrackingService.class);

    private static final String MESSAGE_ID_PREFIX = "message:id:";
    private static final String IDEMPOTENCY_KEY_PREFIX = "message:idempotency:";

    private final RedisTemplate<String, Object> redisTemplate;

    @Value("${app.redis.message-tracking.ttl:86400}") // 24 horas por defecto
    private long messageTtlSeconds;

    public RedisMessageTrackingService(RedisTemplate<String, Object> redisTemplate) {
        this.redisTemplate = redisTemplate;
    }

    /**
     * Registra un nuevo mensaje para tracking.
     *
     * @param message El mensaje a trackear
     */
    public void trackMessage(EntidadBancariaMessage message) {
        try {
            String messageIdKey = MESSAGE_ID_PREFIX + message.getMessageId();
            String idempotencyKey = IDEMPOTENCY_KEY_PREFIX + message.getIdempotencyKey();

            // Guardar mensaje completo por messageId
            redisTemplate.opsForValue().set(
                messageIdKey,
                message,
                Duration.ofSeconds(messageTtlSeconds)
            );

            // Guardar referencia por idempotencyKey
            redisTemplate.opsForValue().set(
                idempotencyKey,
                message.getMessageId(),
                Duration.ofSeconds(messageTtlSeconds)
            );

            logger.debug("Mensaje registrado en Redis - messageId: {}, TTL: {}s",
                message.getMessageId(), messageTtlSeconds);

        } catch (Exception e) {
            logger.error("Error registrando mensaje en Redis: {}", e.getMessage(), e);
            // En producción, aquí podríamos tener un fallback strategy
        }
    }

    /**
     * Actualiza el estado de un mensaje.
     *
     * @param message El mensaje con el estado actualizado
     */
    public void updateStatus(EntidadBancariaMessage message) {
        try {
            String messageIdKey = MESSAGE_ID_PREFIX + message.getMessageId();

            // Actualizar mensaje manteniendo el TTL existente
            Long ttl = redisTemplate.getExpire(messageIdKey);

            if (ttl != null && ttl > 0) {
                redisTemplate.opsForValue().set(
                    messageIdKey,
                    message,
                    Duration.ofSeconds(ttl)
                );
            } else {
                // Si no tiene TTL o expiró, usar el default
                redisTemplate.opsForValue().set(
                    messageIdKey,
                    message,
                    Duration.ofSeconds(messageTtlSeconds)
                );
            }

            logger.debug("Estado actualizado en Redis - messageId: {}, status: {}",
                message.getMessageId(), message.getStatus());

        } catch (Exception e) {
            logger.error("Error actualizando mensaje en Redis: {}", e.getMessage(), e);
        }
    }

    /**
     * Obtiene el estado de un mensaje por su messageId.
     *
     * @param messageId El ID del mensaje
     * @return Optional con el mensaje si existe
     */
    public Optional<EntidadBancariaMessage> getByMessageId(String messageId) {
        try {
            String key = MESSAGE_ID_PREFIX + messageId;
            Object value = redisTemplate.opsForValue().get(key);

            if (value instanceof EntidadBancariaMessage message) {
                return Optional.of(message);
            }

            return Optional.empty();

        } catch (Exception e) {
            logger.error("Error obteniendo mensaje de Redis: {}", e.getMessage(), e);
            return Optional.empty();
        }
    }

    /**
     * Obtiene el estado de un mensaje por su idempotencyKey.
     *
     * @param idempotencyKey La clave de idempotencia
     * @return Optional con el mensaje si existe
     */
    public Optional<EntidadBancariaMessage> getByIdempotencyKey(String idempotencyKey) {
        try {
            String key = IDEMPOTENCY_KEY_PREFIX + idempotencyKey;
            Object messageIdObj = redisTemplate.opsForValue().get(key);

            if (messageIdObj instanceof String messageId) {
                return getByMessageId(messageId);
            }

            return Optional.empty();

        } catch (Exception e) {
            logger.error("Error obteniendo mensaje por idempotency key de Redis: {}", e.getMessage(), e);
            return Optional.empty();
        }
    }

    /**
     * Obtiene todos los mensajes trackeados.
     *
     * @return Collection de todos los mensajes
     */
    public Collection<EntidadBancariaMessage> getAllMessages() {
        try {
            Set<String> keys = redisTemplate.keys(MESSAGE_ID_PREFIX + "*");

            if (keys == null || keys.isEmpty()) {
                return Set.of();
            }

            return keys.stream()
                .map(key -> {
                    Object value = redisTemplate.opsForValue().get(key);
                    return value instanceof EntidadBancariaMessage ? (EntidadBancariaMessage) value : null;
                })
                .filter(message -> message != null)
                .collect(Collectors.toSet());

        } catch (Exception e) {
            logger.error("Error obteniendo todos los mensajes de Redis: {}", e.getMessage(), e);
            return Set.of();
        }
    }

    /**
     * Elimina un mensaje del tracking.
     *
     * @param messageId El ID del mensaje a eliminar
     */
    public void removeMessage(String messageId) {
        try {
            // Primero obtener el mensaje para saber su idempotencyKey
            Optional<EntidadBancariaMessage> messageOpt = getByMessageId(messageId);

            if (messageOpt.isPresent()) {
                EntidadBancariaMessage message = messageOpt.get();

                // Eliminar ambas keys
                redisTemplate.delete(MESSAGE_ID_PREFIX + messageId);
                redisTemplate.delete(IDEMPOTENCY_KEY_PREFIX + message.getIdempotencyKey());

                logger.debug("Mensaje eliminado de Redis - messageId: {}", messageId);
            }

        } catch (Exception e) {
            logger.error("Error eliminando mensaje de Redis: {}", e.getMessage(), e);
        }
    }

    /**
     * Limpia mensajes completados o fallidos más antiguos que el tiempo especificado.
     * Nota: Con Redis + TTL, esto es menos crítico, pero útil para cleanup manual.
     *
     * @param maxAgeMinutes Edad máxima en minutos
     */
    public void cleanOldMessages(int maxAgeMinutes) {
        try {
            var cutoffTime = java.time.Instant.now().minusSeconds(maxAgeMinutes * 60L);

            Collection<EntidadBancariaMessage> allMessages = getAllMessages();

            allMessages.stream()
                .filter(message ->
                    message.getCreatedAt().isBefore(cutoffTime) &&
                    (message.getStatus() == EntidadBancariaMessage.MessageStatus.COMPLETED ||
                     message.getStatus() == EntidadBancariaMessage.MessageStatus.FAILED ||
                     message.getStatus() == EntidadBancariaMessage.MessageStatus.DUPLICATE))
                .forEach(message -> removeMessage(message.getMessageId()));

            logger.info("Cleanup de mensajes antiguos completado");

        } catch (Exception e) {
            logger.error("Error en cleanup de mensajes: {}", e.getMessage(), e);
        }
    }

    /**
     * Obtiene estadísticas del tracking.
     *
     * @return Map con estadísticas
     */
    public Map<String, Long> getStatistics() {
        try {
            Collection<EntidadBancariaMessage> messages = getAllMessages();

            return Map.of(
                "total", (long) messages.size(),
                "pending", messages.stream()
                    .filter(m -> m.getStatus() == EntidadBancariaMessage.MessageStatus.PENDING)
                    .count(),
                "processing", messages.stream()
                    .filter(m -> m.getStatus() == EntidadBancariaMessage.MessageStatus.PROCESSING)
                    .count(),
                "completed", messages.stream()
                    .filter(m -> m.getStatus() == EntidadBancariaMessage.MessageStatus.COMPLETED)
                    .count(),
                "failed", messages.stream()
                    .filter(m -> m.getStatus() == EntidadBancariaMessage.MessageStatus.FAILED)
                    .count(),
                "duplicate", messages.stream()
                    .filter(m -> m.getStatus() == EntidadBancariaMessage.MessageStatus.DUPLICATE)
                    .count()
            );

        } catch (Exception e) {
            logger.error("Error obteniendo estadísticas: {}", e.getMessage(), e);
            return Map.of("total", 0L);
        }
    }
}
