package com.santander.challenge.domain.port;

import com.santander.challenge.domain.model.EntidadBancariaDomain;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Puerto del repositorio (interface) - Clean Architecture
 * Define el contrato que debe cumplir cualquier implementación de persistencia
 * Parte del dominio, sin dependencias de frameworks
 */
public interface EntidadBancariaRepositoryPort {

    /**
     * Guarda o actualiza una entidad bancaria
     * @param entidad la entidad a guardar
     * @return la entidad guardada con su ID generado
     */
    EntidadBancariaDomain save(EntidadBancariaDomain entidad);

    /**
     * Busca una entidad bancaria por ID
     * @param id el identificador único
     * @return Optional con la entidad si existe
     */
    Optional<EntidadBancariaDomain> findById(UUID id);

    /**
     * Obtiene todas las entidades bancarias
     * @return lista de todas las entidades
     */
    List<EntidadBancariaDomain> findAll();

    /**
     * Elimina una entidad bancaria por ID
     * @param id el identificador único
     */
    void deleteById(UUID id);

    /**
     * Verifica si existe una entidad con el ID dado
     * @param id el identificador único
     * @return true si existe, false en caso contrario
     */
    boolean existsById(UUID id);

    /**
     * Busca una entidad bancaria por ID con bloqueo pesimista
     * @param id el identificador único
     * @return la entidad bloqueada para actualización
     */
    EntidadBancariaDomain findByIdForUpdate(UUID id);
}
