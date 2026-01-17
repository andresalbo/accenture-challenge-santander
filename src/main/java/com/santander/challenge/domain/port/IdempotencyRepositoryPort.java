package com.santander.challenge.domain.port;

import java.util.Optional;

/**
 * Puerto para el repositorio de idempotencia - Clean Architecture
 */
public interface IdempotencyRepositoryPort {

    /**
     * Guarda una clave de idempotencia
     * @param key la clave de idempotencia
     * @return true si se guardó exitosamente, false si ya existía
     */
    boolean saveKey(String key);

    /**
     * Verifica si existe una clave de idempotencia
     * @param key la clave a verificar
     * @return true si existe, false en caso contrario
     */
    boolean existsKey(String key);

    /**
     * Busca una clave de idempotencia
     * @param key la clave a buscar
     * @return Optional con la clave si existe
     */
    Optional<String> findKey(String key);

    /**
     * Elimina una clave de idempotencia
     * @param key la clave a eliminar
     */
    void deleteKey(String key);
}
