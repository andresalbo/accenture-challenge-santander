package com.santander.challenge.infrastructure.repository;

import com.santander.challenge.infrastructure.persistence.entity.EntidadBancariaEntity;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

/**
 * Repositorio JPA - Spring Data
 * Proporciona operaciones CRUD automáticas
 */
@Repository
public interface EntidadBancariaJpaRepository extends JpaRepository<EntidadBancariaEntity, UUID> {

    /**
     * Busca una entidad con bloqueo pesimista para actualización
     */
    @Query("SELECT e FROM EntidadBancariaEntity e WHERE e.id = :id")
    Optional<EntidadBancariaEntity> findByIdForUpdate(@Param("id") UUID id);
}
