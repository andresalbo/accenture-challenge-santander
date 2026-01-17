package com.santander.challenge.infrastructure.kafka.service;

import java.util.Collection;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import com.santander.challenge.infrastructure.kafka.dto.EntidadBancariaMessage;

/**
 * Servicio para tracking del estado de los mensajes enviados a Kafka.
 * Permite consultar el estado de procesamiento de solicitudes asíncronas.
 * 
 * En producción, esto debería persistirse en Redis o base de datos
 * para soportar múltiples instancias de la aplicación.
 */
@Service
public class MessageTrackingService {

    private static final Logger logger = LoggerFactory.getLogger(MessageTrackingService.class);

    // Cache en memoria para tracking de mensajes (usar Redis en producción)
    private final Map<String, EntidadBancariaMessage> messagesByMessageId = new ConcurrentHashMap<>();
    private final Map<String, EntidadBancariaMessage> messagesByIdempotencyKey = new ConcurrentHashMap<>();

    /**
     * Registra un nuevo mensaje para tracking.
     * 
     * @param message El mensaje a trackear
     */
    public void trackMessage(EntidadBancariaMessage message) {
        messagesByMessageId.put(message.getMessageId(), message);
        messagesByIdempotencyKey.put(message.getIdempotencyKey(), message);
        logger.debug("Mensaje registrado para tracking - messageId: {}", message.getMessageId());
    }

    /**
     * Actualiza el estado de un mensaje.
     * 
     * @param message El mensaje con el estado actualizado
     */
    public void updateStatus(EntidadBancariaMessage message) {
        messagesByMessageId.put(message.getMessageId(), message);
        messagesByIdempotencyKey.put(message.getIdempotencyKey(), message);
        logger.debug("Estado actualizado - messageId: {}, status: {}", 
                message.getMessageId(), message.getStatus());
    }

    /**
     * Obtiene el estado de un mensaje por su messageId.
     * 
     * @param messageId El ID del mensaje
     * @return Optional con el mensaje si existe
     */
    public Optional<EntidadBancariaMessage> getByMessageId(String messageId) {
        return Optional.ofNullable(messagesByMessageId.get(messageId));
    }

    /**
     * Obtiene el estado de un mensaje por su idempotencyKey.
     * 
     * @param idempotencyKey La clave de idempotencia
     * @return Optional con el mensaje si existe
     */
    public Optional<EntidadBancariaMessage> getByIdempotencyKey(String idempotencyKey) {
        return Optional.ofNullable(messagesByIdempotencyKey.get(idempotencyKey));
    }

    /**
     * Obtiene todos los mensajes trackeados.
     * 
     * @return Collection de todos los mensajes
     */
    public Collection<EntidadBancariaMessage> getAllMessages() {
        return messagesByMessageId.values();
    }

    /**
     * Elimina un mensaje del tracking (limpieza).
     * 
     * @param messageId El ID del mensaje a eliminar
     */
    public void removeMessage(String messageId) {
        EntidadBancariaMessage message = messagesByMessageId.remove(messageId);
        if (message != null) {
            messagesByIdempotencyKey.remove(message.getIdempotencyKey());
            logger.debug("Mensaje eliminado del tracking - messageId: {}", messageId);
        }
    }

    /**
     * Limpia mensajes completados o fallidos más antiguos que el tiempo especificado.
     * Útil para evitar memory leaks.
     * 
     * @param maxAgeMinutes Edad máxima en minutos
     */
    public void cleanOldMessages(int maxAgeMinutes) {
        var cutoffTime = java.time.Instant.now().minusSeconds(maxAgeMinutes * 60L);
        
        messagesByMessageId.entrySet().removeIf(entry -> {
            var message = entry.getValue();
            boolean shouldRemove = message.getCreatedAt().isBefore(cutoffTime) &&
                    (message.getStatus() == EntidadBancariaMessage.MessageStatus.COMPLETED ||
                     message.getStatus() == EntidadBancariaMessage.MessageStatus.FAILED ||
                     message.getStatus() == EntidadBancariaMessage.MessageStatus.DUPLICATE);
            
            if (shouldRemove) {
                messagesByIdempotencyKey.remove(message.getIdempotencyKey());
            }
            return shouldRemove;
        });
    }

    /**
     * Obtiene estadísticas del tracking.
     * 
     * @return Map con estadísticas
     */
    public Map<String, Long> getStatistics() {
        return Map.of(
            "total", (long) messagesByMessageId.size(),
            "pending", messagesByMessageId.values().stream()
                    .filter(m -> m.getStatus() == EntidadBancariaMessage.MessageStatus.PENDING)
                    .count(),
            "processing", messagesByMessageId.values().stream()
                    .filter(m -> m.getStatus() == EntidadBancariaMessage.MessageStatus.PROCESSING)
                    .count(),
            "completed", messagesByMessageId.values().stream()
                    .filter(m -> m.getStatus() == EntidadBancariaMessage.MessageStatus.COMPLETED)
                    .count(),
            "failed", messagesByMessageId.values().stream()
                    .filter(m -> m.getStatus() == EntidadBancariaMessage.MessageStatus.FAILED)
                    .count(),
            "duplicate", messagesByMessageId.values().stream()
                    .filter(m -> m.getStatus() == EntidadBancariaMessage.MessageStatus.DUPLICATE)
                    .count()
        );
    }
}
