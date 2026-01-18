package com.santander.challenge.infrastructure.persistence.entity;

import jakarta.persistence.*;
import java.util.UUID;

/**
 * Entidad JPA - Capa de infraestructura
 * Representa la tabla en la base de datos
 */
@Entity
@Table(name = "entidad_bancaria")
public class EntidadBancariaEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(nullable = false)
    private String nombre;

    @Column(name = "codigo_bcra", nullable = false, length = 10)
    private String codigoBcra;

    @Column(nullable = false)
    private String pais;

    public EntidadBancariaEntity() {
    }

    public EntidadBancariaEntity(UUID id, String nombre, String codigoBcra, String pais) {
        this.id = id;
        this.nombre = nombre;
        this.codigoBcra = codigoBcra;
        this.pais = pais;
    }

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
}
