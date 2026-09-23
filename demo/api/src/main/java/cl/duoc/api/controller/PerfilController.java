package cl.duoc.api.controller;

import java.util.List;

import cl.duoc.api.dto.PerfilResponse;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api")
public class PerfilController {

	@GetMapping("/me")
	public PerfilResponse perfil(@AuthenticationPrincipal Jwt jwt) {
		List<String> roles = jwt.getClaimAsStringList("roles");
		return new PerfilResponse(
			jwt.getClaimAsString("name"),
			jwt.getClaimAsString("preferred_username"),
			roles != null ? roles : List.of()
		);
	}
}
