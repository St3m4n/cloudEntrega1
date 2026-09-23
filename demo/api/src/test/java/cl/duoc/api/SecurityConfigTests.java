package cl.duoc.api;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.oauth2.jwt.Jwt;

class SecurityConfigTests {

	@Test
	void convierteScopesYRolesDeEntraEnAutoridades() {
		Jwt jwt = Jwt.withTokenValue("token")
			.header("alg", "none")
			.claim("scp", "access_as_user")
			.claim("roles", List.of("Admin", "Operator"))
			.build();

		List<String> autoridades = SecurityConfig.jwtAuthenticationConverter()
			.convert(jwt)
			.getAuthorities().stream()
			.map(GrantedAuthority::getAuthority)
			.toList();

		assertThat(autoridades).contains(
			"SCOPE_access_as_user", "ROLE_Admin", "ROLE_Operator");
	}

	@Test
	void tokenSinRolesSoloTieneScopes() {
		Jwt jwt = Jwt.withTokenValue("token")
			.header("alg", "none")
			.claim("scp", "access_as_user")
			.build();

		List<String> autoridades = SecurityConfig.jwtAuthenticationConverter()
			.convert(jwt)
			.getAuthorities().stream()
			.map(GrantedAuthority::getAuthority)
			.toList();

		assertThat(autoridades).contains("SCOPE_access_as_user");
		assertThat(autoridades).noneMatch(a -> a.startsWith("ROLE_"));
	}
}
