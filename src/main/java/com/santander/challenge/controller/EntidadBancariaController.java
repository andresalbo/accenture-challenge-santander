package com.santander.challenge.controller;

import java.util.List;
import java.util.UUID;

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

import com.santander.challenge.model.EntidadBancaria;
import com.santander.challenge.service.EntidadBancariaService;
import com.santander.challenge.service.IdempotencyService;
import com.santander.challenge.service.ConcurrentEntidadBancariaService;
import com.santander.challenge.service.ConcurrentEntidadBancariaService.CreationResult;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;

@RestController
@RequestMapping("/api/entidades-bancarias")
@Tag(name = "Entidades Bancarias", description = "CRUD de entidades bancarias")
public class EntidadBancariaController {

    private final EntidadBancariaService service;
    private final IdempotencyService idempotencyService;
    private final ConcurrentEntidadBancariaService concurrentService;

    public EntidadBancariaController(
            EntidadBancariaService service,
            IdempotencyService idempotencyService,
            ConcurrentEntidadBancariaService concurrentService) {
        this.service = service;
        this.idempotencyService = idempotencyService;
        this.concurrentService = concurrentService;
    }

    @Operation(summary = "Listar todas las entidades bancarias")
    @GetMapping
    public List<EntidadBancaria> listar() {
        return service.listar();
    }

    @Operation(summary = "Obtener entidad bancaria por ID")
    @GetMapping("/{id}")
    public ResponseEntity<EntidadBancaria> obtener(@PathVariable UUID id) {
        return service.obtenerPorId(id)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    @Operation(summary = "Crear una nueva entidad bancaria")
    @PostMapping("/create")
    public ResponseEntity<String> createEntity(
            @RequestHeader(value = "Idempotency-Key") String idempotencyKey,
            @RequestBody EntidadBancaria request) {

        boolean canProcess = idempotencyService.checkAndSaveKey(idempotencyKey);

        if (!canProcess) {
            return ResponseEntity.status(HttpStatus.CONFLICT)
                    .body("Duplicate request detected");
        }

        return ResponseEntity.status(HttpStatus.CREATED).body(service.guardar(request).toString());
        
    }
    
    @Operation(summary = "Crear una nueva entidad bancaria - Solución alternativa para entornos distribuidos")
    @PostMapping("/create2")
    public ResponseEntity<Object> createEntity2(
            @RequestHeader(value = "Idempotency-Key") String idempotencyKey,
            @Valid @RequestBody EntidadBancaria request) {

        // NOTA: Este enfoque tiene limitaciones conceptuales:
        // - processEntity usa el idempotencyKey como ID de entidad (no es el patrón estándar)
        // - Solo funciona si el cliente envía el UUID de la entidad como Idempotency-Key
        // - El bloqueo pesimista solo aplica si la entidad ya existe
        // Para un enfoque más estándar, usar checkAndSaveKey como en createEntity

        try {
            // Verificar si la entidad con este ID ya existe (bloqueo pesimista)
            idempotencyService.processEntity(idempotencyKey);

            // IMPORTANTE: Asignar el idempotencyKey como ID de la entidad
            // para que coincida con lo que se busca en processEntity
            request.setId(UUID.fromString(idempotencyKey));

            // Guardar la entidad con el ID especificado
            EntidadBancaria savedEntity = service.guardar(request);

            // Retornar 201 CREATED (código HTTP correcto para creaciones)
            return ResponseEntity.status(HttpStatus.CREATED).body(savedEntity);

        } catch (IllegalStateException e) {
            // La entidad con este ID ya existe (duplicado)
            return ResponseEntity.status(HttpStatus.CONFLICT)
                    .body("Duplicate request detected - Entity already exists");
        } catch (IllegalArgumentException e) {
            // Error al parsear el UUID del Idempotency-Key
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body("Invalid Idempotency-Key format. Must be a valid UUID");
        } catch (Exception e) {
            e.printStackTrace();
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body("Error processing request: " + e.getMessage());
        }
    }

    @Operation(
        summary = "Crear entidad bancaria - Solución con manejo de concurrencia (Java 21)",
        description = """
            Endpoint mejorado que resuelve los problemas de race conditions usando:
            - ReentrantLock para serializar peticiones con el mismo idempotency-key
            - ConcurrentHashMap para cache thread-safe
            - Compatible con Virtual Threads de Java 21
            
            Ventajas sobre createEntity2:
            ✅ Thread-safe real (previene race conditions)
            ✅ No depende de bloqueos pesimistas de BD
            ✅ Funciona en entornos distribuidos (con cache compartido)
            ✅ Mejor rendimiento bajo carga concurrente
            ✅ Timeout configurable para prevenir deadlocks
            """
    )
    @PostMapping("/create3")
    public ResponseEntity<Object> createEntity3(
            @RequestHeader(value = "Idempotency-Key") String idempotencyKey,
            @Valid @RequestBody EntidadBancaria request) {

        // Usar el servicio concurrente que maneja locks automáticamente
        CreationResult result = concurrentService.createWithIdempotency(idempotencyKey, request);

        // Pattern matching con sealed interfaces (Java 21)
        return switch (result) {
            case CreationResult.Success success ->
                ResponseEntity.status(HttpStatus.CREATED).body(success.entity());

            case CreationResult.Duplicate duplicate ->
                ResponseEntity.status(HttpStatus.CONFLICT).body(duplicate.message());

            case CreationResult.Error error -> {
                // Determinar el código de error apropiado
                if (error.message().contains("Invalid UUID")) {
                    yield ResponseEntity.status(HttpStatus.BAD_REQUEST).body(error.message());
                } else if (error.message().contains("timeout")) {
                    yield ResponseEntity.status(HttpStatus.REQUEST_TIMEOUT).body(error.message());
                } else {
                    yield ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(error.message());
                }
            }
        };
    }

    @Operation(summary = "Actualizar una entidad bancaria existente")
    @PutMapping("/{id}")
    public ResponseEntity<EntidadBancaria> actualizar(@PathVariable UUID id, @Valid @RequestBody EntidadBancaria datos) {
        return service.obtenerPorId(id)
                .map(entidad -> {
                    entidad.setNombre(datos.getNombre());
                    entidad.setCodigoBcra(datos.getCodigoBcra());
                    entidad.setPais(datos.getPais());
                    return ResponseEntity.ok(service.guardar(entidad));
                })
                .orElse(ResponseEntity.notFound().build());
    }

    @Operation(summary = "Eliminar una entidad bancaria")
    @DeleteMapping("/{id}")
    public ResponseEntity<Object> eliminar(@PathVariable UUID id) {
        return service.obtenerPorId(id).map(entidad -> {
            service.eliminar(id);
            return ResponseEntity.noContent().build();
        }).orElse(ResponseEntity.notFound().build());
    }
}