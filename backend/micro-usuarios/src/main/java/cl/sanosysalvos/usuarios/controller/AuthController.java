package cl.sanosysalvos.usuarios.controller;

import cl.sanosysalvos.usuarios.dto.AuthResponseDto;
import cl.sanosysalvos.usuarios.dto.UsuarioDto;
import cl.sanosysalvos.usuarios.model.Usuario;
import cl.sanosysalvos.usuarios.security.JwtTokenProvider;
import cl.sanosysalvos.usuarios.service.UsuarioService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

/**
 * Endpoints de autenticación y perfil de usuario.
 *
 * GET  /api/auth/me          → perfil del usuario autenticado (JWT requerido)
 * POST /api/auth/logout      → instrucción al frontend de borrar el token
 * GET  /api/usuarios/{id}    → perfil público de un usuario por ID
 *
 * El login con Google NO pasa por aquí — Spring Security maneja el flujo
 * OAuth2 automáticamente en /oauth2/authorize/google
 */
@Slf4j
@RestController
@RequiredArgsConstructor
@Tag(name = "Auth", description = "Autenticación y perfil de usuario")
public class AuthController {

    private final UsuarioService usuarioService;
    private final JwtTokenProvider jwtTokenProvider;

    /**
     * Retorna el perfil del usuario autenticado.
     * El userId viene del JWT (ya validado por JwtAuthFilter).
     *
     * Usado por el frontend para hidratar el estado de sesión.
     */
    @GetMapping("/api/auth/me")
    @Operation(summary = "Perfil del usuario autenticado",
               security = @SecurityRequirement(name = "bearerAuth"))
    public ResponseEntity<AuthResponseDto> me(
            @AuthenticationPrincipal UUID userId) {

        return usuarioService.findById(userId)
                .map(u -> {
                    String token = jwtTokenProvider.generateToken(
                            u.getId(), u.getEmail(), u.getNombreCompleto());

                    return ResponseEntity.ok(AuthResponseDto.builder()
                            .token(token)
                            .expiresIn(jwtTokenProvider.getExpirationMs())
                            .usuario(UsuarioDto.from(u))
                            .build());
                })
                .orElse(ResponseEntity.notFound().build());
    }

    /**
     * Logout — el JWT es stateless, así que el backend solo confirma.
     * El frontend es responsable de eliminar el token almacenado.
     */
    @PostMapping("/api/auth/logout")
    @Operation(summary = "Cerrar sesión",
               security = @SecurityRequirement(name = "bearerAuth"))
    public ResponseEntity<Void> logout() {
        // Con JWT stateless no hay nada que invalidar en el servidor.
        // Para revocación real, implementar una blacklist en Redis (fase futura).
        return ResponseEntity.noContent().build();
    }

    /**
     * Retorna el perfil público de un usuario por su ID.
     * Usado por Micro 2 y Micro 3 para enriquecer datos de reportes.
     */
    @GetMapping("/api/usuarios/{id}")
    @Operation(summary = "Perfil de usuario por ID",
               security = @SecurityRequirement(name = "bearerAuth"))
    public ResponseEntity<UsuarioDto> getUsuario(@PathVariable UUID id) {
        return usuarioService.findById(id)
                .map(u -> ResponseEntity.ok(UsuarioDto.from(u)))
                .orElse(ResponseEntity.notFound().build());
    }
}
