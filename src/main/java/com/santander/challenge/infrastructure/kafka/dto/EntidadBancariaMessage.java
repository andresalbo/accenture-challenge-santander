package com.santander.challenge.infrastructure.kafka.dto;

import java.io.Serializable;
import java.time.Instant;
import java.util.UUID;

/**
 * DTO para mensajes de Kafka que representan solicitudes de creación de EntidadBancaria.
 * Incluye metadatos para tracking y trazabilidad.
 */
public class EntidadBancariaMessage implements Serializable {
    
    private static final long serialVersionUID = 1L;
    
    private String messageId;
    private String idempotencyKey;
    private String nombre;
    private String codigoBcra;
    private String pais;
    private Instant createdAt;
    private MessageStatus status;
    private String errorMessage;
    private UUID entityId;
    
    public enum MessageStatus {
        PENDING,
        PROCESSING,
        COMPLETED,
        FAILED,
        DUPLICATE
    }
    
    // Constructor vacío para deserialización
    public EntidadBancariaMessage() {
        this.messageId = UUID.randomUUID().toString();
        this.createdAt = Instant.now();
        this.status = MessageStatus.PENDING;
    }
    
    // Constructor con datos de entidad
    public EntidadBancariaMessage(String idempotencyKey, String nombre, String codigoBcra, String pais) {
        this();
        this.idempotencyKey = idempotencyKey;
        this.nombre = nombre;
        this.codigoBcra = codigoBcra;
        this.pais = pais;
    }
    
    // Getters y Setters
    public String getMessageId() {
        return messageId;
    }
    
    public void setMessageId(String messageId) {
        this.messageId = messageId;
    }
    
    public String getIdempotencyKey() {
        return idempotencyKey;
    }
    
    public void setIdempotencyKey(String idempotencyKey) {
        this.idempotencyKey = idempotencyKey;
    }
    
    public String getNombre() {
        return nombre;
    }
    
    public void setNombre(String nombre) {
        this.nombre = nombre;
    }
    
    public String getCodigoBcra() {
        return codigoBcra;
    }
    
    public void setCodigoBcra(String codigoBcra) {
        this.codigoBcra = codigoBcra;
    }
    
    public String getPais() {
        return pais;
    }
    
    public void setPais(String pais) {
        this.pais = pais;
    }
    
    public Instant getCreatedAt() {
        return createdAt;
    }
    
    public void setCreatedAt(Instant createdAt) {
        this.createdAt = createdAt;
    }
    
    public MessageStatus getStatus() {
        return status;
    }
    
    public void setStatus(MessageStatus status) {
        this.status = status;
    }
    
    public String getErrorMessage() {
        return errorMessage;
    }
    
    public void setErrorMessage(String errorMessage) {
        this.errorMessage = errorMessage;
    }
    
    public UUID getEntityId() {
        return entityId;
    }
    
    public void setEntityId(UUID entityId) {
        this.entityId = entityId;
    }
    
    @Override
    public String toString() {
        return "EntidadBancariaMessage{" +
                "messageId='" + messageId + '\'' +
                ", idempotencyKey='" + idempotencyKey + '\'' +
                ", nombre='" + nombre + '\'' +
                ", codigoBcra='" + codigoBcra + '\'' +
                ", pais='" + pais + '\'' +
                ", status=" + status +
                ", createdAt=" + createdAt +
                '}';
    }
}
