package cl.duoc.pedidos360.common;

import java.util.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.*;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.oauth2.server.resource.authentication.*;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.web.cors.*;

@Configuration
@EnableMethodSecurity
@io.swagger.v3.oas.annotations.security.SecurityScheme(name="bearerAuth",type=io.swagger.v3.oas.annotations.enums.SecuritySchemeType.HTTP,scheme="bearer",bearerFormat="JWT")
@io.swagger.v3.oas.annotations.security.SecurityRequirement(name="bearerAuth")
public class SecurityConfig {
    @Bean SecurityFilterChain security(HttpSecurity http) throws Exception {
        var scopes = new JwtGrantedAuthoritiesConverter();
        var roles = new JwtGrantedAuthoritiesConverter();
        roles.setAuthoritiesClaimName("roles"); roles.setAuthorityPrefix("ROLE_");
        var converter = new JwtAuthenticationConverter();
        converter.setJwtGrantedAuthoritiesConverter(jwt -> {
            Collection<GrantedAuthority> all = new ArrayList<>(scopes.convert(jwt));
            all.addAll(roles.convert(jwt)); return all;
        });
        return http.csrf(c -> c.disable()).cors(Customizer.withDefaults())
            .sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
            .authorizeHttpRequests(a -> a
                .requestMatchers("/actuator/health").permitAll()
                .requestMatchers("/v3/api-docs/**", "/swagger-ui/**", "/swagger-ui.html").hasRole("Admin")
                .requestMatchers("/api/admin/**").hasRole("Admin")
                .requestMatchers(HttpMethod.GET, "/api/audit/**").hasAnyRole("Admin", "Auditor")
                .requestMatchers(HttpMethod.GET, "/api/report/**").hasRole("Admin")
                .requestMatchers("/api/audit/**", "/api/report/**").denyAll()
                .requestMatchers("/api/catalog/reservations/**").hasAnyRole("Admin", "Operator")
                .requestMatchers(HttpMethod.GET, "/api/catalog/**").hasAnyRole("Admin", "Operator", "Customer")
                .requestMatchers("/api/catalog/**").hasRole("Admin")
                .requestMatchers(HttpMethod.GET, "/api/orders/**").hasAnyRole("Admin", "Operator", "Customer")
                .requestMatchers(HttpMethod.POST, "/api/orders").hasAnyRole("Operator", "Customer")
                .requestMatchers("/api/orders/**").hasAnyRole("Admin", "Operator")
                .requestMatchers("/api/me", "/api/data").hasAuthority("SCOPE_access_as_user")
                .anyRequest().denyAll())
            .oauth2ResourceServer(o -> o.jwt(j -> j.jwtAuthenticationConverter(converter))).build();
    }
    @Bean CorsConfigurationSource cors(@Value("${app.cors-origins}") String origins) {
        var c = new CorsConfiguration(); c.setAllowedOrigins(Arrays.asList(origins.split(",")));
        c.setAllowedMethods(List.of("GET","POST","PUT","DELETE","OPTIONS"));
        c.setAllowedHeaders(List.of("Authorization","Content-Type","Idempotency-Key","X-Correlation-Id"));
        var source = new UrlBasedCorsConfigurationSource(); source.registerCorsConfiguration("/**",c); return source;
    }
}
