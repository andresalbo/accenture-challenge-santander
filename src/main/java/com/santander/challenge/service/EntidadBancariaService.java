package com.santander.challenge.service;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.stereotype.Service;

import com.santander.challenge.model.EntidadBancaria;
import com.santander.challenge.repository.EntidadBancariaRepository;

@Service
public class EntidadBancariaService {

    private final EntidadBancariaRepository repository;

    public EntidadBancariaService(EntidadBancariaRepository repository) {
        this.repository = repository;
    }

    public List<EntidadBancaria> listar() {
        return repository.findAll();
    }

    public Optional<EntidadBancaria> obtenerPorId(UUID id) {
        return repository.findById(id);
    }

    public EntidadBancaria guardar(EntidadBancaria entidad) {
        return repository.save(entidad);
    }

    public void eliminar(UUID id) {
        repository.deleteById(id);
    }
}