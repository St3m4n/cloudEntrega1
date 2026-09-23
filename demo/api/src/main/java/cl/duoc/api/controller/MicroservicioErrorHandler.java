package cl.duoc.api.controller;

import java.util.Map;

import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClientResponseException;

@RestControllerAdvice
public class MicroservicioErrorHandler {

	// Propaga al frontend el mismo estado que devolvio el microservicio (400, 404, etc.)
	// en vez de convertirlo en un 500 generico.
	@ExceptionHandler(RestClientResponseException.class)
	public ResponseEntity<String> errorDelMicroservicio(RestClientResponseException ex) {
		return ResponseEntity.status(ex.getStatusCode())
			.contentType(MediaType.APPLICATION_JSON)
			.body(ex.getResponseBodyAsString());
	}

	@ExceptionHandler(ResourceAccessException.class)
	public ResponseEntity<Map<String, String>> microservicioNoDisponible(ResourceAccessException ex) {
		return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
			.body(Map.of("error", "Microservicio no disponible"));
	}
}
