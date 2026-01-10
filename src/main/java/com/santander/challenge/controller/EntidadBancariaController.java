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

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;

@RestController
@RequestMapping("/api/entidades-bancarias")
@Tag(name = "Entidades Bancarias", description = "CRUD de entidades bancarias")
public class EntidadBancariaController {

    private final EntidadBancariaService service;
    private final IdempotencyService idempotencyService;

    public EntidadBancariaController(EntidadBancariaService service, IdempotencyService idempotencyService) {
        this.service = service;
        this.idempotencyService = idempotencyService;
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
    
    @Operation(summary = "Crear una nueva entidad bancaria - Solucion alternativa para entornos distribuidos")
    @PostMapping("/create2")
    public ResponseEntity<Object> createEntity2(@RequestHeader(value = "Idempotency-Key") String idempotencyKey,
            @RequestBody EntidadBancaria request, String a) {
        try {
        	idempotencyService.processEntity(idempotencyKey);
            return ResponseEntity.ok(service.guardar(request));
        } catch (IllegalStateException e) {
            return ResponseEntity.status(409).body("already processed");
        } catch (Exception e) {
        	e.printStackTrace();
            return ResponseEntity.internalServerError().body("Error processing");
        }
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