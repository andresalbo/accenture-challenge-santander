package com.santander.challenge.repository;

import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.santander.challenge.model.EntidadBancaria;

import jakarta.persistence.LockModeType;

public interface EntidadBancariaRepository extends JpaRepository<EntidadBancaria, UUID> {
	
	@Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT p FROM EntidadBancaria p WHERE p.id = :id")
    EntidadBancaria findByIdForUpdate(@Param("id") UUID id);
}