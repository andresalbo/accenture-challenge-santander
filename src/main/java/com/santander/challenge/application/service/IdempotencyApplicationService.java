package com.santander.challenge.application.service;

import com.santander.challenge.domain.port.IdempotencyRepositoryPort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Servicio de aplicación para idempotencia - Clean Architecture
 */
@Service
@Transactional
public class IdempotencyApplicationService {

    private final IdempotencyRepositoryPort repositoryPort;

    public IdempotencyApplicationService(IdempotencyRepositoryPort repositoryPort) {
        this.repositoryPort = repositoryPort;
    }

    /**
     * Caso de uso: Verificar y guardar clave de idempotencia
     * @param key la clave de idempotencia
     * @return true si la clave es nueva y se guardó, false si ya existía
     */
    public boolean checkAndSaveKey(String key) {
        if (key == null || key.isBlank()) {
            throw new IllegalArgumentException("La clave de idempotencia no puede estar vacía");
        }

        // Verificar si ya existe
        if (repositoryPort.existsKey(key)) {
            return false; // Duplicado detectado
        }

        // Guardar la nueva clave
        return repositoryPort.saveKey(key);
    }

    /**
     * Caso de uso: Verificar si una clave existe
     */
    @Transactional(readOnly = true)
    public boolean existeKey(String key) {
        return repositoryPort.existsKey(key);
    }

    /**
     * Caso de uso: Eliminar una clave de idempotencia
     */
    public void eliminarKey(String key) {
        repositoryPort.deleteKey(key);
    }
}
