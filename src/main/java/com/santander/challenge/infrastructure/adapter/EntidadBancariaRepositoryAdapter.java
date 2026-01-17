package com.santander.challenge.infrastructure.adapter;

import com.santander.challenge.domain.model.EntidadBancariaDomain;
import com.santander.challenge.domain.port.EntidadBancariaRepositoryPort;
import com.santander.challenge.infrastructure.mapper.EntidadBancariaMapper;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Adaptador del repositorio - Clean Architecture
 * Implementa el puerto del dominio usando JPA
 * Traduce entre el dominio y la infraestructura
 */
@Component
public class EntidadBancariaRepositoryAdapter implements EntidadBancariaRepositoryPort {

    private final EntidadBancariaRepositoryPort jpaRepository;
    private final EntidadBancariaMapper mapper;

    public EntidadBancariaRepositoryAdapter(
            EntidadBancariaRepositoryPort jpaRepository,
            EntidadBancariaMapper mapper) {
        this.jpaRepository = jpaRepository;
        this.mapper = mapper;
    }

    @Override
    public EntidadBancariaDomain save(EntidadBancariaDomain entidad) {
        // Convertir de dominio a JPA
        EntidadBancariaDomain jpaEntity = mapper.toEntity(entidad);

        // Guardar usando JPA
        EntidadBancariaDomain savedEntity = jpaRepository.save(jpaEntity);

        // Convertir de JPA a dominio
        return mapper.toDomain(savedEntity);
    }

    @Override
    public Optional<EntidadBancariaDomain> findById(UUID id) {
        return jpaRepository.findById(id)
                .map(mapper::toDomain);
    }

    @Override
    public List<EntidadBancariaDomain> findAll() {
        return jpaRepository.findAll()
                .stream()
                .map(mapper::toDomain)
                .collect(Collectors.toList());
    }

    @Override
    public void deleteById(UUID id) {
        jpaRepository.deleteById(id);
    }

    @Override
    public boolean existsById(UUID id) {
        return jpaRepository.existsById(id);
    }

    @Override
    public EntidadBancariaDomain findByIdForUpdate(UUID id) {
        EntidadBancariaDomain entity = jpaRepository.findByIdForUpdate(id);
        return mapper.toDomain(entity);
    }
}
