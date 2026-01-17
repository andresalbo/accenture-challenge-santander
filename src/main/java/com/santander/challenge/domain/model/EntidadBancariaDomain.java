package com.santander.challenge.domain.model;

import java.util.UUID;

/**
 * Entidad de dominio - Clean Architecture
 * No tiene dependencias de frameworks (sin anotaciones JPA/Spring)
 */
public class EntidadBancariaDomain {

    private UUID id;
    private String nombre;
    private String codigoBcra;
    private String pais;

    // Constructor vacío
    public EntidadBancariaDomain() {
    }

    // Constructor completo
    public EntidadBancariaDomain(UUID id, String nombre, String codigoBcra, String pais) {
        this.id = id;
        this.nombre = nombre;
        this.codigoBcra = codigoBcra;
        this.pais = pais;
    }

    // Constructor sin ID (para creación)
    public EntidadBancariaDomain(String nombre, String codigoBcra, String pais) {
        this.nombre = nombre;
        this.codigoBcra = codigoBcra;
        this.pais = pais;
    }

    // Métodos de validación de dominio
    public void validar() {
        if (nombre == null || nombre.isBlank()) {
            throw new IllegalArgumentException("El nombre es obligatorio");
        }
        if (codigoBcra == null || codigoBcra.isBlank()) {
            throw new IllegalArgumentException("El código BCRA es obligatorio");
        }
        if (codigoBcra.length() < 3 || codigoBcra.length() > 10) {
            throw new IllegalArgumentException("El código BCRA debe tener entre 3 y 10 caracteres");
        }
        if (pais == null || pais.isBlank()) {
            throw new IllegalArgumentException("El país es obligatorio");
        }
    }

    // Lógica de negocio (ejemplo)
    public boolean esArgentina() {
        return "Argentina".equalsIgnoreCase(this.pais);
    }

    // Getters y setters
    public UUID getId() {
        return id;
    }

    public void setId(UUID id) {
        this.id = id;
    }

    public String getNombre() {
        return nombre;
    }

    public void setNombre(String nombre) {
        this.nombre = nombre;
    }

    public String getCodigoBcra() {
        return codigoBcra;
    }

    public void setCodigoBcra(String codigoBcra) {
        this.codigoBcra = codigoBcra;
    }

    public String getPais() {
        return pais;
    }

    public void setPais(String pais) {
        this.pais = pais;
    }

    @Override
    public String toString() {
        return "EntidadBancariaDomain{" +
                "id=" + id +
                ", nombre='" + nombre + '\'' +
                ", codigoBcra='" + codigoBcra + '\'' +
                ", pais='" + pais + '\'' +
                '}';
    }
}
