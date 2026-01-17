package com.santander.challenge.controller;

import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.santander.challenge.application.service.EntidadBancariaApplicationService;
import com.santander.challenge.application.service.IdempotencyApplicationService;
import com.santander.challenge.domain.model.EntidadBancariaDomain;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;

/**
 * Controlador con Clean Architecture
 * Usa los servicios de aplicación en lugar de acceso directo al repositorio
 */
@RestController
@RequestMapping("/api/v2/entidades-bancarias")
@Tag(name = "Entidades Bancarias V2 (Clean Architecture)", description = "CRUD con arquitectura hexagonal")
public class EntidadBancariaCleanController {

    private final EntidadBancariaApplicationService applicationService;
    private final IdempotencyApplicationService idempotencyService;

    public EntidadBancariaCleanController(
            EntidadBancariaApplicationService applicationService,
            IdempotencyApplicationService idempotencyService) {
        this.applicationService = applicationService;
        this.idempotencyService = idempotencyService;
    }

    @Operation(summary = "Listar todas las entidades bancarias")
    @GetMapping
    public ResponseEntity<List<EntidadBancariaDTO>> listar() {
        List<EntidadBancariaDTO> dtos = applicationService.listarTodas()
                .stream()
                .map(this::toDTO)
                .collect(Collectors.toList());
        return ResponseEntity.ok(dtos);
    }

    @Operation(summary = "Obtener entidad bancaria por ID")
    @GetMapping("/{id}")
    public ResponseEntity<EntidadBancariaDTO> obtener(@PathVariable UUID id) {
        return applicationService.obtenerPorId(id)
                .map(this::toDTO)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    @Operation(summary = "Crear una nueva entidad bancaria con idempotencia")
    @PostMapping
    public ResponseEntity<Object> crear(
            @RequestHeader(value = "Idempotency-Key") String idempotencyKey,
            @Valid @RequestBody EntidadBancariaCreateRequest request) {

        try {
            // Verificar idempotencia
            boolean canProcess = idempotencyService.checkAndSaveKey(idempotencyKey);

            if (!canProcess) {
                return ResponseEntity.status(HttpStatus.CONFLICT)
                        .body(new ErrorResponse("Duplicate request detected"));
            }

            // Convertir DTO a dominio
            EntidadBancariaDomain domainEntity = toDomain(request);

            // Ejecutar caso de uso
            EntidadBancariaDomain savedEntity = applicationService.crear(domainEntity);

            // Retornar DTO
            return ResponseEntity.status(HttpStatus.CREATED)
                    .body(toDTO(savedEntity));

        } catch (IllegalArgumentException e) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body(new ErrorResponse(e.getMessage()));
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(new ErrorResponse("Error processing request: " + e.getMessage()));
        }
    }

    @Operation(summary = "Actualizar una entidad bancaria existente")
    @PutMapping("/{id}")
    public ResponseEntity<Object> actualizar(
            @PathVariable UUID id,
            @Valid @RequestBody EntidadBancariaUpdateRequest request) {

        try {
            // Convertir DTO a dominio
            EntidadBancariaDomain datosActualizados = toDomain(request);

            // Ejecutar caso de uso
            EntidadBancariaDomain actualizada = applicationService.actualizar(id, datosActualizados);

            // Retornar DTO
            return ResponseEntity.ok(toDTO(actualizada));

        } catch (IllegalArgumentException e) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(new ErrorResponse(e.getMessage()));
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(new ErrorResponse("Error updating entity: " + e.getMessage()));
        }
    }

    @Operation(summary = "Eliminar una entidad bancaria")
    @DeleteMapping("/{id}")
    public ResponseEntity<Object> eliminar(@PathVariable UUID id) {
        try {
            applicationService.eliminar(id);
            return ResponseEntity.noContent().build();
        } catch (IllegalArgumentException e) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(new ErrorResponse(e.getMessage()));
        }
    }

    // === DTOs ===

    /**
     * DTO para crear entidad bancaria
     */
    public record EntidadBancariaCreateRequest(
            String nombre,
            String codigoBcra,
            String pais
    ) {}

    /**
     * DTO para actualizar entidad bancaria
     */
    public record EntidadBancariaUpdateRequest(
            String nombre,
            String codigoBcra,
            String pais
    ) {}

    /**
     * DTO para respuesta
     */
    public record EntidadBancariaDTO(
            UUID id,
            String nombre,
            String codigoBcra,
            String pais
    ) {}

    /**
     * DTO para errores
     */
    public record ErrorResponse(String message) {}

    // === Mappers ===

    private EntidadBancariaDomain toDomain(EntidadBancariaCreateRequest dto) {
        return new EntidadBancariaDomain(
                dto.nombre(),
                dto.codigoBcra(),
                dto.pais()
        );
    }

    private EntidadBancariaDomain toDomain(EntidadBancariaUpdateRequest dto) {
        return new EntidadBancariaDomain(
                dto.nombre(),
                dto.codigoBcra(),
                dto.pais()
        );
    }

    private EntidadBancariaDTO toDTO(EntidadBancariaDomain domain) {
        return new EntidadBancariaDTO(
                domain.getId(),
                domain.getNombre(),
                domain.getCodigoBcra(),
                domain.getPais()
        );
    }
}
