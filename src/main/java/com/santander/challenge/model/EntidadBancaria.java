package com.santander.challenge.model;

import java.util.UUID;

import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

@Entity
public class EntidadBancaria {

    @Id
    @GeneratedValue(strategy = GenerationType.AUTO)
    private UUID id;

    @NotBlank(message = "El nombre es obligatorio")
    private String nombre;

    @NotBlank(message = "El código BCRA es obligatorio")
    @Size(min = 3, max = 10)
    private String codigoBcra;

    @NotBlank(message = "El país es obligatorio")
    private String pais;
 // Getters y setters
    public UUID getId() { return id; }
    public void setId(UUID id) { this.id = id; }

    public String getNombre() { return nombre; }
    public void setNombre(String nombre) { this.nombre = nombre; }

    public String getCodigoBcra() { return codigoBcra; }
    public void setCodigoBcra(String codigoBcra) { this.codigoBcra = codigoBcra; }

    public String getPais() { return pais; }
    public void setPais(String pais) { this.pais = pais; }
}
