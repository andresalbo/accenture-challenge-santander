package com.santander.challenge.infrastructure.mapper;

import com.santander.challenge.domain.model.EntidadBancariaDomain;
import org.springframework.stereotype.Component;

/**
 * Mapper entre entidad de dominio y entidad JPA
 * Parte de la capa de infraestructura
 */
@Component
public class EntidadBancariaMapper {

    /**
     * Convierte de entidad JPA a entidad de dominio
     */
    public EntidadBancariaDomain toDomain(EntidadBancariaDomain entity) {
        if (entity == null) {
            return null;
        }

        return new EntidadBancariaDomain(
                entity.getId(),
                entity.getNombre(),
                entity.getCodigoBcra(),
                entity.getPais()
        );
    }

    /**
     * Convierte de entidad de dominio a entidad JPA
     */
    public EntidadBancariaDomain toEntity(EntidadBancariaDomain domain) {
        if (domain == null) {
            return null;
        }

        EntidadBancariaDomain entity = new EntidadBancariaDomain();
        entity.setId(domain.getId());
        entity.setNombre(domain.getNombre());
        entity.setCodigoBcra(domain.getCodigoBcra());
        entity.setPais(domain.getPais());

        return entity;
    }

    /**
     * Actualiza una entidad JPA existente con datos del dominio
     */
    public void updateEntity(EntidadBancariaDomain entity, EntidadBancariaDomain domain) {
        if (entity == null || domain == null) {
            return;
        }

        entity.setNombre(domain.getNombre());
        entity.setCodigoBcra(domain.getCodigoBcra());
        entity.setPais(domain.getPais());
    }
}
