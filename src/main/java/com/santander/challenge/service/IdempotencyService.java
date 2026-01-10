package com.santander.challenge.service;

import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.santander.challenge.model.EntidadBancaria;
import com.santander.challenge.model.IdempotencyKey;
import com.santander.challenge.repository.EntidadBancariaRepository;
import com.santander.challenge.repository.IdempotencyKeyRepository;

@Service
public class IdempotencyService {

	private final IdempotencyKeyRepository repository;
	private final EntidadBancariaRepository repositoryEB;

	public IdempotencyService(IdempotencyKeyRepository repository, EntidadBancariaRepository repositoryEB) {
		this.repository = repository;
		this.repositoryEB = repositoryEB;
	}

	@Transactional
	public boolean checkAndSaveKey(String key) {
		if (repository.findByKeyValue(key).isPresent()) {
			return false; // Ya existe
		}
		IdempotencyKey entity = new IdempotencyKey();
		entity.setKeyValue(key);
		repository.save(entity);
		return true; // Nuevo key, se puede procesar
	}

	@Transactional
	public EntidadBancaria processEntity(String id) {
		String uuidStringInput = id;
		UUID uuidObject = UUID.fromString(uuidStringInput);
		EntidadBancaria eb = repositoryEB.findByIdForUpdate(uuidObject);
		if (eb != null) {
			if ((uuidObject.equals(eb.getId()))) {
				throw new IllegalStateException("Already processed");
			}
		}

		return eb;
	}
}