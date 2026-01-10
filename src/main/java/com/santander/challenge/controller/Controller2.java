package com.santander.challenge.controller;

import java.util.List;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.client.RestTemplate;

import com.santander.challenge.model.EntidadBancaria;

import io.swagger.v3.oas.annotations.Operation;

@RestController
@RequestMapping("/api")
public class Controller2 {
	
	@Autowired
	private RestTemplate restTemplate;

	@Operation(summary = "Obtener lista duplicada llamando internamente al API")
	@GetMapping("/duplicado")
	public ResponseEntity<List<EntidadBancaria>> listarDuplicado() {
	    String url = "http://localhost:8080/api/entidades-bancarias";
	    ResponseEntity<EntidadBancaria[]> response = restTemplate.getForEntity(url, EntidadBancaria[].class);

	    if (response.getStatusCode().is2xxSuccessful() && response.getBody() != null) {
	        return ResponseEntity.ok(List.of(response.getBody()));
	    } else {
	        return ResponseEntity.status(response.getStatusCode()).build();
	    }
	}

}
