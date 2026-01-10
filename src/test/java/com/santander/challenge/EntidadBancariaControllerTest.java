package com.santander.challenge;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.santander.challenge.controller.EntidadBancariaController;
import com.santander.challenge.model.EntidadBancaria;
import com.santander.challenge.service.EntidadBancariaService;
import com.santander.challenge.service.IdempotencyService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.util.*;
import java.util.concurrent.*;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(EntidadBancariaController.class)
class EntidadBancariaControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private EntidadBancariaService service;

    @MockBean
    private IdempotencyService idempotencyService;

    private ObjectMapper objectMapper;
    private EntidadBancaria entidad;

    @BeforeEach
    void setUp() {
        objectMapper = new ObjectMapper();
        entidad = new EntidadBancaria();
        entidad.setId(UUID.randomUUID());
        entidad.setNombre("Banco Test");
        entidad.setCodigoBcra("123");
        entidad.setPais("AR");
    }

    @Test
    void listarEntidades_ShouldReturnList() throws Exception {
        Mockito.when(service.listar()).thenReturn(List.of(entidad));

        mockMvc.perform(get("/api/entidades-bancarias"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].nombre").value("Banco Test"));
    }

    @Test
    void obtenerEntidad_ShouldReturnEntity_WhenExists() throws Exception {
        Mockito.when(service.obtenerPorId(entidad.getId())).thenReturn(Optional.of(entidad));

        mockMvc.perform(get("/api/entidades-bancarias/" + entidad.getId()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.nombre").value("Banco Test"));
    }

    @Test
    void obtenerEntidad_ShouldReturn404_WhenNotExists() throws Exception {
        Mockito.when(service.obtenerPorId(any())).thenReturn(Optional.empty());

        mockMvc.perform(get("/api/entidades-bancarias/" + UUID.randomUUID()))
                .andExpect(status().isNotFound());
    }

    @Test
    void createEntity_ShouldReturn201_WhenIdempotencyOk() throws Exception {
        Mockito.when(idempotencyService.checkAndSaveKey("key123")).thenReturn(true);
        Mockito.when(service.guardar(any(EntidadBancaria.class))).thenReturn(entidad);

        mockMvc.perform(post("/api/entidades-bancarias/create")
                        .header("Idempotency-Key", "key123")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(entidad)))
                .andExpect(status().isCreated())
                .andExpect(content().string(entidad.toString()));
    }

    @Test
    void createEntity_ShouldReturn409_WhenDuplicate() throws Exception {
        Mockito.when(idempotencyService.checkAndSaveKey("key123")).thenReturn(false);

        mockMvc.perform(post("/api/entidades-bancarias/create")
                        .header("Idempotency-Key", "key123")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(entidad)))
                .andExpect(status().isConflict())
                .andExpect(content().string("Duplicate request detected"));
    }

    @Test
    void createEntity2_ShouldReturn200_WhenProcessed() throws Exception {
    	Mockito.when(idempotencyService.processEntity("key123")).thenReturn(null);
        Mockito.when(service.guardar(any(EntidadBancaria.class))).thenReturn(entidad);

        mockMvc.perform(post("/api/entidades-bancarias/create2")
                        .header("Idempotency-Key", "key123")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(entidad)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.nombre").value("Banco Test"))
                .andExpect(jsonPath("$.codigoBcra").value("123"))
                .andExpect(jsonPath("$.pais").value("AR"));
    }

    @Test
    void createEntity2_ShouldReturn409_WhenAlreadyProcessed() throws Exception {
        Mockito.doThrow(new IllegalStateException("already processed"))
                .when(idempotencyService).processEntity("key123");

        mockMvc.perform(post("/api/entidades-bancarias/create2")
                        .header("Idempotency-Key", "key123")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(entidad)))
                .andExpect(status().isConflict())
                .andExpect(content().string("already processed"));
    }

    @Test
    void actualizarEntidad_ShouldReturn200_WhenExists() throws Exception {
        Mockito.when(service.obtenerPorId(entidad.getId())).thenReturn(Optional.of(entidad));
        Mockito.when(service.guardar(any())).thenReturn(entidad);

        mockMvc.perform(put("/api/entidades-bancarias/" + entidad.getId())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(entidad)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.nombre").value("Banco Test"));
    }

    @Test
    void actualizarEntidad_ShouldReturn404_WhenNotExists() throws Exception {
        Mockito.when(service.obtenerPorId(any())).thenReturn(Optional.empty());

        mockMvc.perform(put("/api/entidades-bancarias/" + UUID.randomUUID())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(entidad)))
                .andExpect(status().isNotFound());
    }

    @Test
    void eliminarEntidad_ShouldReturn204_WhenExists() throws Exception {
        Mockito.when(service.obtenerPorId(entidad.getId())).thenReturn(Optional.of(entidad));
        Mockito.doNothing().when(service).eliminar(entidad.getId());

        mockMvc.perform(delete("/api/entidades-bancarias/" + entidad.getId()))
                .andExpect(status().isNoContent());
    }

    @Test
    void eliminarEntidad_ShouldReturn404_WhenNotExists() throws Exception {
        Mockito.when(service.obtenerPorId(any())).thenReturn(Optional.empty());

        mockMvc.perform(delete("/api/entidades-bancarias/" + UUID.randomUUID()))
                .andExpect(status().isNotFound());
    }

    @Test
    void createEntity2_ShouldHandleConcurrentRequests() throws Exception {
        // Simulamos que la primera es OK, la segunda lanza IllegalStateException
        Mockito.doAnswer(invocation -> {
            Thread.sleep(100); // Simula demora
            return null;
        }).doThrow(new IllegalStateException("already processed"))
                .when(idempotencyService).processEntity("key123");

        Mockito.when(service.guardar(any())).thenReturn(entidad);

        Callable<Integer> request = () -> mockMvc.perform(post("/api/entidades-bancarias/create2")
                        .header("Idempotency-Key", "key123")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(entidad)))
                .andReturn()
                .getResponse()
                .getStatus();

        ExecutorService executor = Executors.newFixedThreadPool(2);
        Future<Integer> f1 = executor.submit(request);
        Future<Integer> f2 = executor.submit(request);

        int status1 = f1.get();
        int status2 = f2.get();

        executor.shutdown();

        assertTrue((status1 == 200 && status2 == 409) || (status1 == 409 && status2 == 200),
                "Expected one 200 OK and one 409 Conflict but got " + status1 + " and " + status2);

        Mockito.verify(idempotencyService, Mockito.times(2)).processEntity("key123");
    }
}
