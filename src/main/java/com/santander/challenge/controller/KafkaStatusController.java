package com.santander.challenge.controller;

import java.util.Collection;
import java.util.Map;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.santander.challenge.infrastructure.kafka.dto.EntidadBancariaMessage;
import com.santander.challenge.infrastructure.kafka.service.MessageTrackingService;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;

/**
 * Controlador para consultar el estado de los mensajes procesados via Kafka.
 * Permite tracking de solicitudes asíncronas de creación de entidades.
 */
@RestController
@RequestMapping("/api/entidades-bancarias/kafka")
@Tag(name = "Kafka Status", description = "Endpoints para consultar estado de procesamiento Kafka")
public class KafkaStatusController {

    private final MessageTrackingService trackingService;

    public KafkaStatusController(MessageTrackingService trackingService) {
        this.trackingService = trackingService;
    }

    @Operation(
        summary = "Consultar estado de un mensaje por messageId",
        description = """
            Retorna el estado actual del procesamiento de una solicitud.
            
            Estados posibles:
            - PENDING: Mensaje enviado a Kafka, esperando procesamiento
            - PROCESSING: Consumer está procesando el mensaje
            - COMPLETED: Entidad creada exitosamente
            - FAILED: Error durante el procesamiento
            - DUPLICATE: Solicitud duplicada detectada
            """
    )
    @GetMapping("/status/{messageId}")
    public ResponseEntity<Object> getStatusByMessageId(@PathVariable String messageId) {
        return trackingService.getByMessageId(messageId)
                .map(message -> {
                    Map<String, Object> response = buildStatusResponse(message);
                    return ResponseEntity.ok((Object) response);
                })
                .orElse(ResponseEntity.status(HttpStatus.NOT_FOUND)
                        .body(Map.of(
                            "error", "Message not found",
                            "messageId", messageId
                        )));
    }

    @Operation(
        summary = "Consultar estado por idempotencyKey",
        description = "Busca el estado del procesamiento usando el Idempotency-Key original"
    )
    @GetMapping("/status/by-key/{idempotencyKey}")
    public ResponseEntity<Object> getStatusByIdempotencyKey(@PathVariable String idempotencyKey) {
        return trackingService.getByIdempotencyKey(idempotencyKey)
                .map(message -> {
                    Map<String, Object> response = buildStatusResponse(message);
                    return ResponseEntity.ok((Object) response);
                })
                .orElse(ResponseEntity.status(HttpStatus.NOT_FOUND)
                        .body(Map.of(
                            "error", "No message found for this idempotency key",
                            "idempotencyKey", idempotencyKey
                        )));
    }

    @Operation(
        summary = "Listar todos los mensajes trackeados",
        description = "Retorna todos los mensajes en el sistema de tracking (útil para debugging)"
    )
    @GetMapping("/messages")
    public ResponseEntity<Collection<EntidadBancariaMessage>> getAllMessages() {
        return ResponseEntity.ok(trackingService.getAllMessages());
    }

    @Operation(
        summary = "Obtener estadísticas de procesamiento",
        description = "Retorna contadores por estado de los mensajes"
    )
    @GetMapping("/statistics")
    public ResponseEntity<Map<String, Long>> getStatistics() {
        return ResponseEntity.ok(trackingService.getStatistics());
    }

    @Operation(
        summary = "Limpiar mensajes antiguos",
        description = "Elimina mensajes completados/fallidos más antiguos que el tiempo especificado"
    )
    @PostMapping("/cleanup")
    public ResponseEntity<Map<String, Object>> cleanupOldMessages(
            @RequestParam(defaultValue = "60") int maxAgeMinutes) {
        
        long beforeCount = trackingService.getAllMessages().size();
        trackingService.cleanOldMessages(maxAgeMinutes);
        long afterCount = trackingService.getAllMessages().size();
        
        return ResponseEntity.ok(Map.of(
            "message", "Cleanup completed",
            "removedCount", beforeCount - afterCount,
            "remainingCount", afterCount
        ));
    }

    /**
     * Construye la respuesta de estado para un mensaje.
     */
    private Map<String, Object> buildStatusResponse(EntidadBancariaMessage message) {
        var responseBuilder = new java.util.HashMap<String, Object>();
        responseBuilder.put("messageId", message.getMessageId());
        responseBuilder.put("idempotencyKey", message.getIdempotencyKey());
        responseBuilder.put("status", message.getStatus().name());
        responseBuilder.put("createdAt", message.getCreatedAt().toString());
        
        if (message.getEntityId() != null) {
            responseBuilder.put("entityId", message.getEntityId().toString());
            responseBuilder.put("entityUrl", "/api/entidades-bancarias/" + message.getEntityId());
        }
        
        if (message.getErrorMessage() != null) {
            responseBuilder.put("errorMessage", message.getErrorMessage());
        }
        
        // Agregar datos de la entidad si está disponible
        responseBuilder.put("entityData", Map.of(
            "nombre", message.getNombre(),
            "codigoBcra", message.getCodigoBcra(),
            "pais", message.getPais()
        ));
        
        return responseBuilder;
    }
}
