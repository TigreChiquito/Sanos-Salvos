package cl.sanosysalvos.orquestador.controller;

import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Mono;

import java.time.Instant;
import java.util.Map;

/**
 * Respuestas de fallback cuando el Circuit Breaker está OPEN.
 *
 * En lugar de devolver un error genérico, cada microservicio tiene su
 * propio mensaje de fallback que el frontend puede manejar correctamente.
 *
 * El frontend debería mostrar un mensaje amigable al usuario cuando
 * reciba una respuesta de fallback (status 503).
 */
@Slf4j
@RestController
public class FallbackController {

    @RequestMapping("/fallback/usuarios")
    public Mono<ResponseEntity<Map<String, Object>>> fallbackUsuarios() {
        log.warn("Circuit Breaker ABIERTO — micro-usuarios no disponible");
        return Mono.just(ResponseEntity
                .status(HttpStatus.SERVICE_UNAVAILABLE)
                .body(buildResponse(
                        "micro-usuarios",
                        "El servicio de autenticación no está disponible temporalmente. " +
                        "Por favor intenta nuevamente en unos momentos."
                )));
    }

    @RequestMapping("/fallback/mascotas")
    public Mono<ResponseEntity<Map<String, Object>>> fallbackMascotas() {
        log.warn("Circuit Breaker ABIERTO — micro-mascotas no disponible");
        return Mono.just(ResponseEntity
                .status(HttpStatus.SERVICE_UNAVAILABLE)
                .body(buildResponse(
                        "micro-mascotas",
                        "El servicio de reportes no está disponible temporalmente. " +
                        "Los datos del mapa pueden estar desactualizados."
                )));
    }

    @RequestMapping("/fallback/coincidencias")
    public Mono<ResponseEntity<Map<String, Object>>> fallbackCoincidencias() {
        log.warn("Circuit Breaker ABIERTO — micro-coincidencias no disponible");
        return Mono.just(ResponseEntity
                .status(HttpStatus.SERVICE_UNAVAILABLE)
                .body(buildResponse(
                        "micro-coincidencias",
                        "El motor de coincidencias no está disponible temporalmente. " +
                        "Los matches se procesarán automáticamente cuando el servicio se recupere."
                )));
    }

    // ── Privado ────────────────────────────────────────────────

    private Map<String, Object> buildResponse(String servicio, String mensaje) {
        return Map.of(
                "error",     "SERVICE_UNAVAILABLE",
                "servicio",  servicio,
                "mensaje",   mensaje,
                "timestamp", Instant.now().toString()
        );
    }
}
