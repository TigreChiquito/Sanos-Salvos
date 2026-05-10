package cl.sanosysalvos.orquestador.filter;

import cl.sanosysalvos.orquestador.security.JwtTokenProvider;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.http.server.reactive.ServerHttpResponse;
import org.springframework.stereotype.Component;
import org.springframework.util.AntPathMatcher;
import org.springframework.util.StringUtils;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.web.server.WebFilter;
import org.springframework.web.server.WebFilterChain;
import reactor.core.publisher.Mono;

import java.util.List;

/**
 * Filtro global del Gateway que valida el JWT en cada request protegida.
 *
 * Si el token es válido:
 *   - Añade headers internos X-User-Id, X-User-Email, X-User-Name
 *     para que los microservicios puedan identificar al usuario
 *     sin necesidad de volver a validar el token.
 *   - Elimina el header Authorization original antes de reenviar
 *     (el token ya fue consumido aquí).
 *
 * Si el token es inválido o falta en una ruta protegida:
 *   - Retorna 401 Unauthorized directamente desde el gateway.
 */
@Slf4j
@Component
@Order(-1)  // Ejecutar antes que los filtros de Spring Security
@RequiredArgsConstructor
public class JwtGatewayFilter implements WebFilter {

    private final JwtTokenProvider jwtTokenProvider;
    private final AntPathMatcher pathMatcher = new AntPathMatcher();

    @Value("${app.public-paths}")
    private List<String> publicPaths;

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, WebFilterChain chain) {
        ServerHttpRequest request = exchange.getRequest();
        String path   = request.getPath().value();
        HttpMethod method = request.getMethod();

        // Las rutas públicas pasan sin validación
        if (isPublicPath(path, method)) {
            return chain.filter(exchange);
        }

        // Extraer Bearer token
        String token = extractToken(request);

        if (!StringUtils.hasText(token)) {
            log.debug("Request sin token hacia ruta protegida: {} {}", method, path);
            return unauthorized(exchange.getResponse());
        }

        if (!jwtTokenProvider.validateToken(token)) {
            log.warn("Token inválido en request: {} {}", method, path);
            return unauthorized(exchange.getResponse());
        }

        // Token válido — enriquecer la request con headers de identidad
        String userId = jwtTokenProvider.getUserId(token).toString();
        String email  = jwtTokenProvider.getEmail(token);
        String name   = jwtTokenProvider.getName(token);

        log.debug("JWT válido — userId: {}, path: {} {}", userId, method, path);

        ServerHttpRequest enrichedRequest = request.mutate()
                .header("X-User-Id",    userId)
                .header("X-User-Email", email)
                .header("X-User-Name",  name)
                // Quitar el Authorization para que los micros no lo revaliden
                // (pueden confiar en X-User-Id que viene del gateway)
                .headers(h -> h.remove(HttpHeaders.AUTHORIZATION))
                .build();

        return chain.filter(exchange.mutate().request(enrichedRequest).build());
    }

    // ── Privado ────────────────────────────────────────────────

    private boolean isPublicPath(String path, HttpMethod method) {
        // GET /api/reportes y GET /api/reportes/{id} son públicos
        if (HttpMethod.GET.equals(method) && path.matches("/api/reportes(/[^/]+)?")) {
            return true;
        }
        return publicPaths.stream()
                .anyMatch(pattern -> pathMatcher.match(pattern, path));
    }

    private String extractToken(ServerHttpRequest request) {
        String header = request.getHeaders().getFirst(HttpHeaders.AUTHORIZATION);
        if (StringUtils.hasText(header) && header.startsWith("Bearer ")) {
            return header.substring(7);
        }
        return null;
    }

    private Mono<Void> unauthorized(ServerHttpResponse response) {
        response.setStatusCode(HttpStatus.UNAUTHORIZED);
        response.getHeaders().add("Content-Type", "application/json");
        var body = response.bufferFactory()
                .wrap("{\"error\":\"No autorizado\",\"message\":\"Token JWT requerido o inválido\"}"
                        .getBytes());
        return response.writeWith(Mono.just(body));
    }
}
