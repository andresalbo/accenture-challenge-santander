package com.santander.challenge;

import com.santander.challenge.model.EntidadBancaria;
import com.santander.challenge.service.ConcurrentEntidadBancariaService;
import com.santander.challenge.service.ConcurrentEntidadBancariaService.CreationResult;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Test de concurrencia para el servicio ConcurrentEntidadBancariaService.
 * Demuestra que la solución previene race conditions correctamente.
 */
@SpringBootTest
public class ConcurrentIdempotencyTest {

    @Autowired
    private ConcurrentEntidadBancariaService concurrentService;

    @Test
    public void testConcurrentRequestsWithSameIdempotencyKey() throws Exception {
        // Arrange: Preparar 100 threads que intentarán crear la misma entidad
        String idempotencyKey = UUID.randomUUID().toString();
        int numberOfThreads = 100;

        EntidadBancaria entity = new EntidadBancaria();
        entity.setNombre("Banco Test Concurrente");
        entity.setCodigoBcra("999");
        entity.setPais("Argentina");

        // Act: Ejecutar 100 peticiones concurrentes con el mismo idempotency-key
        ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor();

        List<CompletableFuture<CreationResult>> futures = new ArrayList<>();
        for (int i = 0; i < numberOfThreads; i++) {
            CompletableFuture<CreationResult> future = CompletableFuture.supplyAsync(
                () -> concurrentService.createWithIdempotency(idempotencyKey, entity),
                executor
            );
            futures.add(future);
        }

        // Esperar a que todas completen
        CompletableFuture.allOf(futures.toArray(new CompletableFuture[0])).join();

        List<CreationResult> results = futures.stream()
                .map(CompletableFuture::join)
                .toList();

        // Assert: Verificar que SOLO 1 fue exitoso y el resto son duplicados
        long successCount = results.stream()
                .filter(r -> r instanceof CreationResult.Success)
                .count();

        long duplicateCount = results.stream()
                .filter(r -> r instanceof CreationResult.Duplicate)
                .count();

        long errorCount = results.stream()
                .filter(r -> r instanceof CreationResult.Error)
                .count();

        System.out.println("=== Resultados del Test de Concurrencia ===");
        System.out.println("Threads ejecutados: " + numberOfThreads);
        System.out.println("Éxitos (201): " + successCount);
        System.out.println("Duplicados (409): " + duplicateCount);
        System.out.println("Errores (500): " + errorCount);

        // Aserciones
        assertEquals(1, successCount, "Solo 1 request debe ser exitoso");
        assertEquals(numberOfThreads - 1, duplicateCount, "El resto deben ser duplicados");
        assertEquals(0, errorCount, "No debe haber errores");

        executor.shutdown();
    }

    @Test
    public void testConcurrentRequestsWithDifferentIdempotencyKeys() throws Exception {
        // Arrange: 50 peticiones con UUIDs diferentes
        int numberOfRequests = 50;

        List<String> idempotencyKeys = new ArrayList<>();
        for (int i = 0; i < numberOfRequests; i++) {
            idempotencyKeys.add(UUID.randomUUID().toString());
        }

        // Act: Ejecutar todas en paralelo
        ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor();

        List<CompletableFuture<CreationResult>> futures = idempotencyKeys.stream()
                .map(key -> CompletableFuture.supplyAsync(() -> {
                    EntidadBancaria entity = new EntidadBancaria();
                    entity.setNombre("Banco " + key.substring(0, 8));
                    entity.setCodigoBcra(String.valueOf((int)(Math.random() * 1000)));
                    entity.setPais("Argentina");
                    return concurrentService.createWithIdempotency(key, entity);
                }, executor))
                .toList();

        CompletableFuture.allOf(futures.toArray(new CompletableFuture[0])).join();

        List<CreationResult> results = futures.stream()
                .map(CompletableFuture::join)
                .toList();

        // Assert: Todas deben ser exitosas (UUIDs únicos)
        long successCount = results.stream()
                .filter(r -> r instanceof CreationResult.Success)
                .count();

        System.out.println("=== Test con UUIDs Diferentes ===");
        System.out.println("Peticiones: " + numberOfRequests);
        System.out.println("Éxitos: " + successCount);

        assertEquals(numberOfRequests, successCount,
            "Todas las peticiones con UUIDs únicos deben ser exitosas");

        executor.shutdown();
    }

    @Test
    public void testIdempotencyWithInvalidUUID() {
        // Arrange
        String invalidKey = "not-a-valid-uuid";
        EntidadBancaria entity = new EntidadBancaria();
        entity.setNombre("Banco Test");
        entity.setCodigoBcra("111");
        entity.setPais("Argentina");

        // Act
        CreationResult result = concurrentService.createWithIdempotency(invalidKey, entity);

        // Assert
        assertTrue(result instanceof CreationResult.Error, "Debe ser un error");
        CreationResult.Error error = (CreationResult.Error) result;
        assertTrue(error.message().contains("Invalid UUID"),
            "El mensaje debe indicar UUID inválido");
    }

    @Test
    public void testMixedScenario() throws Exception {
        // Arrange: 200 peticiones mezcladas
        // - 100 con el mismo UUID (UUID-A)
        // - 50 con otro UUID (UUID-B)
        // - 50 con UUIDs únicos

        String uuidA = UUID.randomUUID().toString();
        String uuidB = UUID.randomUUID().toString();

        List<String> keys = new ArrayList<>();

        // 100 duplicados del UUID-A
        for (int i = 0; i < 100; i++) {
            keys.add(uuidA);
        }

        // 50 duplicados del UUID-B
        for (int i = 0; i < 50; i++) {
            keys.add(uuidB);
        }

        // 50 únicos
        for (int i = 0; i < 50; i++) {
            keys.add(UUID.randomUUID().toString());
        }

        // Act: Ejecutar todas concurrentemente
        ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor();

        List<CompletableFuture<CreationResult>> futures = keys.stream()
                .map(key -> CompletableFuture.supplyAsync(() -> {
                    EntidadBancaria entity = new EntidadBancaria();
                    entity.setNombre("Banco " + key.substring(0, 8));
                    entity.setCodigoBcra(String.valueOf((int)(Math.random() * 1000)));
                    entity.setPais("Argentina");
                    return concurrentService.createWithIdempotency(key, entity);
                }, executor))
                .toList();

        CompletableFuture.allOf(futures.toArray(new CompletableFuture[0])).join();

        List<CreationResult> results = futures.stream()
                .map(CompletableFuture::join)
                .toList();

        // Assert
        long successCount = results.stream()
                .filter(r -> r instanceof CreationResult.Success)
                .count();

        long duplicateCount = results.stream()
                .filter(r -> r instanceof CreationResult.Duplicate)
                .count();

        System.out.println("=== Test Escenario Mixto ===");
        System.out.println("Total peticiones: " + keys.size());
        System.out.println("Éxitos esperados: 52 (1 UUID-A + 1 UUID-B + 50 únicos)");
        System.out.println("Éxitos obtenidos: " + successCount);
        System.out.println("Duplicados esperados: 148 (99 UUID-A + 49 UUID-B)");
        System.out.println("Duplicados obtenidos: " + duplicateCount);

        assertEquals(52, successCount,
            "Debe haber exactamente 52 éxitos (1+1+50)");
        assertEquals(148, duplicateCount,
            "Debe haber exactamente 148 duplicados (99+49)");

        executor.shutdown();
    }
}

