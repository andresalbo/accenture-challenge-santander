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

	/**
	 * Verifica si una entidad con el ID especificado ya existe en la base de datos.
	 * Usa bloqueo pesimista (PESSIMISTIC_WRITE) para prevenir condiciones de carrera.
	 *
	 * LIMITACIONES DE ESTE ENFOQUE:
	 * - Mezcla el concepto de "Idempotency-Key" con "Entity ID"
	 * - Requiere que el cliente envíe el UUID de la entidad como Idempotency-Key
	 * - No es el patrón estándar de idempotencia (usar checkAndSaveKey es mejor)
	 * - El bloqueo pesimista solo funciona si la entidad ya existe
	 *
	 * FUNCIONAMIENTO:
	 * - Si la entidad NO existe → retorna null → permite continuar con el CREATE
	 * - Si la entidad SÍ existe → lanza IllegalStateException → rechaza como duplicado
	 *
	 * @param id El ID de la entidad a buscar (generalmente el Idempotency-Key del header)
	 * @return null si la entidad no existe, lanza excepción si existe
	 * @throws IllegalStateException si la entidad ya existe (duplicado)
	 * @throws IllegalArgumentException si el ID no es un UUID válido
	 */
	@Transactional
	public EntidadBancaria processEntity(String id) {
		// Convertir el String a UUID (puede lanzar IllegalArgumentException)
		UUID uuidObject = UUID.fromString(id);

		// Buscar la entidad con bloqueo pesimista para evitar race conditions
		// PESSIMISTIC_WRITE bloquea la fila si existe, previniendo modificaciones concurrentes
		EntidadBancaria eb = repositoryEB.findByIdForUpdate(uuidObject);

		if (eb != null) {
			// La entidad ya existe, es un duplicado
			throw new IllegalStateException("Already processed");
		}

		// La entidad no existe, se puede proceder con el CREATE
		return null;
	}
}