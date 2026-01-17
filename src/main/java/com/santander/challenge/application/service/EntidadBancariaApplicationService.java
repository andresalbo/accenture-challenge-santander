package com.santander.challenge.application.service;

import com.santander.challenge.domain.model.EntidadBancariaDomain;
import com.santander.challenge.domain.port.EntidadBancariaRepositoryPort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Servicio de aplicación - Clean Architecture
 * Implementa los casos de uso de negocio
 * Coordina las operaciones entre el dominio y la infraestructura
 */
@Service
@Transactional
public class EntidadBancariaApplicationService {

    private final EntidadBancariaRepositoryPort repositoryPort;

    public EntidadBancariaApplicationService(EntidadBancariaRepositoryPort repositoryPort) {
        this.repositoryPort = repositoryPort;
    }

    /**
     * Caso de uso: Crear una nueva entidad bancaria
     */
    public EntidadBancariaDomain crear(EntidadBancariaDomain entidad) {
        // Validar reglas de dominio
        entidad.validar();

        // Guardar en el repositorio
        return repositoryPort.save(entidad);
    }

    /**
     * Caso de uso: Actualizar una entidad bancaria existente
     */
    public EntidadBancariaDomain actualizar(UUID id, EntidadBancariaDomain datosActualizados) {
        // Buscar la entidad existente
        EntidadBancariaDomain entidadExistente = repositoryPort.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Entidad bancaria no encontrada con ID: " + id));

        // Actualizar los datos
        entidadExistente.setNombre(datosActualizados.getNombre());
        entidadExistente.setCodigoBcra(datosActualizados.getCodigoBcra());
        entidadExistente.setPais(datosActualizados.getPais());

        // Validar reglas de dominio
        entidadExistente.validar();

        // Guardar cambios
        return repositoryPort.save(entidadExistente);
    }

    /**
     * Caso de uso: Obtener una entidad bancaria por ID
     */
    @Transactional(readOnly = true)
    public Optional<EntidadBancariaDomain> obtenerPorId(UUID id) {
        return repositoryPort.findById(id);
    }

    /**
     * Caso de uso: Listar todas las entidades bancarias
     */
    @Transactional(readOnly = true)
    public List<EntidadBancariaDomain> listarTodas() {
        return repositoryPort.findAll();
    }

    /**
     * Caso de uso: Eliminar una entidad bancaria
     */
    public void eliminar(UUID id) {
        if (!repositoryPort.existsById(id)) {
            throw new IllegalArgumentException("Entidad bancaria no encontrada con ID: " + id);
        }
        repositoryPort.deleteById(id);
    }

    /**
     * Caso de uso: Verificar si una entidad existe
     */
    @Transactional(readOnly = true)
    public boolean existe(UUID id) {
        return repositoryPort.existsById(id);
    }
}
