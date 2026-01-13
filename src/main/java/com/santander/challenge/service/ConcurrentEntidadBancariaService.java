package com.santander.challenge.service;

import java.util.UUID;
import java.util.concurrent.CompletableFuture;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.santander.challenge.model.EntidadBancaria;
import com.santander.challenge.repository.EntidadBancariaRepository;
import com.santander.challenge.service.ConcurrentIdempotencyService.ProcessingResult;

/**
 * Servicio mejorado para crear entidades bancarias con manejo de concurrencia.
 * Usa características de Java 21: Virtual Threads, Records, Pattern Matching.
 */
@Service
public class ConcurrentEntidadBancariaService {

    private final EntidadBancariaRepository repository;
    private final EntidadBancariaService entidadService;
    private final ConcurrentIdempotencyService idempotencyService;

    public ConcurrentEntidadBancariaService(
            EntidadBancariaRepository repository,
            EntidadBancariaService entidadService,
            ConcurrentIdempotencyService idempotencyService) {
        this.repository = repository;
        this.entidadService = entidadService;
        this.idempotencyService = idempotencyService;
    }

    /**
     * Resultado de la creación de una entidad
     */
    public sealed interface CreationResult {
        record Success(EntidadBancaria entity) implements CreationResult {}
        record Duplicate(String message) implements CreationResult {}
        record Error(String message) implements CreationResult {}
    }

    /**
     * Crea una entidad bancaria con idempotencia garantizada.
     * Thread-safe y compatible con múltiples peticiones concurrentes.
     *
     * @param idempotencyKey UUID único para la petición
     * @param entity Entidad a crear
     * @return CreationResult con el resultado de la operación
     */
    public CreationResult createWithIdempotency(String idempotencyKey, EntidadBancaria entity) {

        // Verificación rápida antes de adquirir el lock
        if (idempotencyService.isAlreadyProcessed(idempotencyKey)) {
            return new CreationResult.Duplicate("Request already processed");
        }

        // Procesar con idempotencia usando locks
        ProcessingResult result = idempotencyService.processWithIdempotency(
            idempotencyKey,
            () -> processEntityCreation(idempotencyKey, entity)
        );

        if (!result.success()) {
            return new CreationResult.Error(result.message());
        }

        // Si fue exitoso, buscar y retornar la entidad creada
        try {
            UUID entityId = UUID.fromString(idempotencyKey);
            return repository.findById(entityId)
                    .<CreationResult>map(CreationResult.Success::new)
                    .orElseGet(() -> new CreationResult.Error("Entity created but not found"));
        } catch (Exception e) {
            return new CreationResult.Error("Error retrieving created entity: " + e.getMessage());
        }
    }

    /**
     * Lógica de creación de la entidad (ejecutada dentro del lock)
     */
    @Transactional
    private ProcessingResult processEntityCreation(String idempotencyKey, EntidadBancaria entity) {
        try {
            // Parsear el UUID
            UUID entityId = UUID.fromString(idempotencyKey);

            // Verificar si ya existe en BD (double-check)
            if (repository.existsById(entityId)) {
                return new ProcessingResult(
                    false,
                    "Entity already exists",
                    System.currentTimeMillis()
                );
            }

            // Asignar el ID y guardar
            entity.setId(entityId);
            entidadService.guardar(entity);

            return new ProcessingResult(
                true,
                "Entity created successfully",
                System.currentTimeMillis()
            );

        } catch (DataIntegrityViolationException e) {
            // La constraint de BD falló (race condition extremadamente raro con los locks)
            return new ProcessingResult(
                false,
                "Duplicate entity detected by database",
                System.currentTimeMillis()
            );
        } catch (IllegalArgumentException e) {
            return new ProcessingResult(
                false,
                "Invalid UUID format: " + e.getMessage(),
                System.currentTimeMillis()
            );
        } catch (Exception e) {
            return new ProcessingResult(
                false,
                "Error creating entity: " + e.getMessage(),
                System.currentTimeMillis()
            );
        }
    }

    /**
     * Crea múltiples entidades en paralelo usando Virtual Threads (Java 21).
     *
     * @param requests Lista de peticiones (key + entity)
     * @return CompletableFuture con los resultados
     */
    public CompletableFuture<java.util.List<CreationResult>> createBatch(
            java.util.List<EntityRequest> requests) {

        // Crear un executor con Virtual Threads
        var executor = java.util.concurrent.Executors.newVirtualThreadPerTaskExecutor();

        return CompletableFuture.supplyAsync(() -> {
            // Procesar cada petición en paralelo usando virtual threads
            var futures = requests.stream()
                    .map(req -> CompletableFuture.supplyAsync(
                            () -> createWithIdempotency(req.idempotencyKey(), req.entity()),
                            executor
                    ))
                    .toList();

            // Esperar a que todas completen
            @SuppressWarnings("unchecked")
            CompletableFuture<CreationResult>[] futuresArray =
                    futures.toArray(new CompletableFuture[0]);
            CompletableFuture.allOf(futuresArray).join();

            // Recolectar resultados
            return futures.stream()
                    .map(CompletableFuture::join)
                    .toList();

        }, executor);
    }

    /**
     * Record para peticiones de creación de entidades
     */
    public record EntityRequest(String idempotencyKey, EntidadBancaria entity) {}
}

