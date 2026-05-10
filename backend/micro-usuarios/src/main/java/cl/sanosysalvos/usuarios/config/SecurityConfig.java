package cl.sanosysalvos.usuarios.config;

import cl.sanosysalvos.usuarios.security.JwtAuthFilter;
import cl.sanosysalvos.usuarios.security.OAuth2SuccessHandler;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import java.util.List;

/**
 * Configuración de seguridad del microservicio.
 *
 * - Stateless: sin sesión HTTP (toda la auth va por JWT)
 * - OAuth2 login habilitado para el flujo Google
 * - CSRF deshabilitado (API REST + JWT, no necesario)
 * - CORS configurado para el frontend Astro
 */
@Configuration
@EnableWebSecurity
@RequiredArgsConstructor
public class SecurityConfig {

    private final JwtAuthFilter jwtAuthFilter;
    private final OAuth2SuccessHandler oauth2SuccessHandler;

    @Value("${app.cors.allowed-origins}")
    private String allowedOrigins;

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        http
            // ── CSRF ───────────────────────────────────────────────
            .csrf(AbstractHttpConfigurer::disable)

            // ── CORS ───────────────────────────────────────────────
            .cors(cors -> cors.configurationSource(corsConfigurationSource()))

            // ── Sesión: stateless (JWT) ────────────────────────────
            .sessionManagement(session ->
                session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))

            // ── Rutas públicas vs protegidas ───────────────────────
            .authorizeHttpRequests(auth -> auth
                // OAuth2 endpoints de Spring Security
                .requestMatchers("/oauth2/**", "/login/oauth2/**").permitAll()
                // Swagger / API docs
                .requestMatchers("/swagger-ui/**", "/api-docs/**").permitAll()
                // Health check
                .requestMatchers("/actuator/health").permitAll()
                // Todo lo demás requiere JWT válido
                .anyRequest().authenticated()
            )

            // ── OAuth2 login (Google) ──────────────────────────────
            .oauth2Login(oauth2 -> oauth2
                .successHandler(oauth2SuccessHandler)
            )

            // ── Filtro JWT ─────────────────────────────────────────
            .addFilterBefore(jwtAuthFilter, UsernamePasswordAuthenticationFilter.class);

        return http.build();
    }

    @Bean
    public CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration config = new CorsConfiguration();
        config.setAllowedOrigins(List.of(allowedOrigins.split(",")));
        config.setAllowedMethods(List.of("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS"));
        config.setAllowedHeaders(List.of("*"));
        config.setAllowCredentials(true);

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", config);
        return source;
    }
}
