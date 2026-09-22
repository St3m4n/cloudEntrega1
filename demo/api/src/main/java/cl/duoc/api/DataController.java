package cl.duoc.api;

import java.util.Map;

import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class DataController {

	@GetMapping("/api/data")
	public Map<String, String> data(
		@AuthenticationPrincipal Jwt jwt
	) {
		return Map.of(
			"mensaje", "Acceso autorizado a Spring Boot",
			"usuarioId", jwt.getSubject()
		);
	}
}
