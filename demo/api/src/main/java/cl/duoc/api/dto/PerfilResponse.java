package cl.duoc.api.dto;

import java.util.List;

public record PerfilResponse(
	String nombre,
	String usuario,
	List<String> roles
) {
}
