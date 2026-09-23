package cl.duoc.api;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationConverter;
import org.springframework.security.oauth2.server.resource.authentication.JwtGrantedAuthoritiesConverter;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

@Configuration
public class SecurityConfig {

	static final String ADMIN = "Admin";
	static final String OPERATOR = "Operator";

	@Bean
	SecurityFilterChain security(HttpSecurity http) throws Exception {
		return http
			.csrf(csrf -> csrf.disable())
			.cors(Customizer.withDefaults())
			.sessionManagement(s -> s.sessionCreationPolicy(
				SessionCreationPolicy.STATELESS
			))
			.authorizeHttpRequests(auth -> auth
				.requestMatchers(HttpMethod.GET, "/api/data", "/api/me")
				.hasAuthority("SCOPE_access_as_user")
				.requestMatchers(HttpMethod.GET, "/api/catalog/**")
				.hasAnyRole(ADMIN, OPERATOR)
				.requestMatchers(HttpMethod.POST, "/api/catalog/**").hasRole(ADMIN)
				.requestMatchers(HttpMethod.PUT, "/api/catalog/**").hasRole(ADMIN)
				.requestMatchers(HttpMethod.DELETE, "/api/catalog/**").hasRole(ADMIN)
				.anyRequest().denyAll()
			)
			.oauth2ResourceServer(oauth ->
				oauth.jwt(jwt -> jwt.jwtAuthenticationConverter(jwtAuthenticationConverter()))
			)
			.build();
	}

	// Entra ID envia los App Roles en el claim "roles"; se exponen como ROLE_<valor>
	// ademas de los SCOPE_<scp> que Spring genera por defecto.
	static JwtAuthenticationConverter jwtAuthenticationConverter() {
		JwtGrantedAuthoritiesConverter scopes = new JwtGrantedAuthoritiesConverter();

		JwtGrantedAuthoritiesConverter roles = new JwtGrantedAuthoritiesConverter();
		roles.setAuthoritiesClaimName("roles");
		roles.setAuthorityPrefix("ROLE_");

		JwtAuthenticationConverter converter = new JwtAuthenticationConverter();
		converter.setJwtGrantedAuthoritiesConverter(jwt -> {
			Collection<GrantedAuthority> authorities = new ArrayList<>(scopes.convert(jwt));
			authorities.addAll(roles.convert(jwt));
			return authorities;
		});
		return converter;
	}

	@Bean
	CorsConfigurationSource corsConfigurationSource() {
		CorsConfiguration configuration = new CorsConfiguration();

		configuration.setAllowedOrigins(
			List.of("http://localhost:5173"));
		configuration.setAllowedMethods(
			List.of("GET", "POST", "PUT", "DELETE", "OPTIONS"));
		configuration.setAllowedHeaders(
			List.of("Authorization", "Content-Type"));

		UrlBasedCorsConfigurationSource source =
			new UrlBasedCorsConfigurationSource();

		source.registerCorsConfiguration("/**", configuration);
		return source;
	}
}
