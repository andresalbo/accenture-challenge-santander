package com.santander.challenge.controller;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.santander.challenge.infrastructure.kafka.dto.EntidadBancariaMessage;
import com.santander.challenge.infrastructure.kafka.producer.EntidadBancariaProducer;
import com.santander.challenge.infrastructure.kafka.service.MessageTrackingService;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;

/**
 * Controller para enviar mensajes a Kafka
 */
@RestController
@RequestMapping("/api/kafka")
@Tag(name = "Kafka Messages", description = "Endpoints para enviar mensajes a Kafka")
public class KafkaMessageController {

    private final EntidadBancariaProducer kafkaProducer;
    private final MessageTrackingService trackingService;

    public KafkaMessageController(
            EntidadBancariaProducer kafkaProducer,
            MessageTrackingService trackingService) {
        this.kafkaProducer = kafkaProducer;
        this.trackingService = trackingService;
    }

    @Operation(
        summary = "Enviar mensaje a Kafka",
        description = "Envía un mensaje de creación de entidad bancaria al topic de Kafka"
    )
    @PostMapping("/send")
    public ResponseEntity<Object> sendMessage(
            @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey,
            @Valid @RequestBody CreateMessageRequest request) {

        try {
            // Generar idempotencyKey si no se proporciona
            String key = (idempotencyKey != null && !idempotencyKey.isBlank()) 
                    ? idempotencyKey 
                    : UUID.randomUUID().toString();

            // Crear mensaje
            EntidadBancariaMessage message = new EntidadBancariaMessage(
                    key,
                    request.nombre(),
                    request.codigoBcra(),
                    request.pais()
            );

            // Registrar mensaje para tracking
            trackingService.trackMessage(message);

            // Enviar a Kafka
            CompletableFuture<?> future = kafkaProducer.sendCreateRequest(message);

            // Esperar resultado (con timeout implícito)
            future.join();

            // Respuesta exitosa
            return ResponseEntity.status(HttpStatus.ACCEPTED)
                    .body(Map.of(
                            "status", "ACCEPTED",
                            "message", "Mensaje enviado a Kafka exitosamente",
                            "messageId", message.getMessageId(),
                            "idempotencyKey", message.getIdempotencyKey(),
                            "topic", "entidad-bancaria-create"
                    ));

        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of(
                            "status", "ERROR",
                            "message", "Error enviando mensaje a Kafka",
                            "error", e.getMessage()
                    ));
        }
    }

    @Operation(
        summary = "Enviar mensaje asíncrono a Kafka",
        description = "Envía un mensaje sin esperar confirmación (fire-and-forget)"
    )
    @PostMapping("/send-async")
    public ResponseEntity<Object> sendMessageAsync(
            @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey,
            @Valid @RequestBody CreateMessageRequest request) {

        // Generar idempotencyKey si no se proporciona
        String key = (idempotencyKey != null && !idempotencyKey.isBlank()) 
                ? idempotencyKey 
                : UUID.randomUUID().toString();

        // Crear mensaje
        EntidadBancariaMessage message = new EntidadBancariaMessage(
                key,
                request.nombre(),
                request.codigoBcra(),
                request.pais()
        );

        // Registrar mensaje para tracking
        trackingService.trackMessage(message);

        // Enviar a Kafka (asíncrono - no esperamos)
        kafkaProducer.sendCreateRequest(message);

        // Respuesta inmediata
        return ResponseEntity.status(HttpStatus.ACCEPTED)
                .body(Map.of(
                        "status", "PENDING",
                        "message", "Mensaje enviado a Kafka (asíncrono)",
                        "messageId", message.getMessageId(),
                        "idempotencyKey", message.getIdempotencyKey(),
                        "topic", "entidad-bancaria-create"
                ));
    }

    /**
     * Request DTO para crear mensaje
     */
    public record CreateMessageRequest(
            @NotBlank(message = "El nombre es requerido")
            String nombre,
            
            @NotBlank(message = "El código BCRA es requerido")
            String codigoBcra,
            
            @NotBlank(message = "El país es requerido")
            String pais
    ) {}
}
