package cl.sanosysalvos.usuarios.controller;

import cl.sanosysalvos.usuarios.dto.NotificacionDto;
import cl.sanosysalvos.usuarios.service.NotificacionService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import java.util.UUID;

@RestController
@RequiredArgsConstructor
@Tag(name = "Notificaciones", description = "Notificaciones del sistema para el usuario autenticado")
public class NotificacionController {

    private final NotificacionService notificacionService;

    @GetMapping("/api/usuarios/me/notificaciones")
    @Operation(summary = "Listar todas las notificaciones del usuario",
               security = @SecurityRequirement(name = "bearerAuth"))
    public ResponseEntity<List<NotificacionDto>> listar(
            @AuthenticationPrincipal UUID userId) {
        return ResponseEntity.ok(notificacionService.listarPorUsuario(userId));
    }

    @GetMapping("/api/usuarios/me/notificaciones/no-leidas")
    @Operation(summary = "Cantidad de notificaciones no leídas",
               security = @SecurityRequirement(name = "bearerAuth"))
    public ResponseEntity<Map<String, Long>> contarNoLeidas(
            @AuthenticationPrincipal UUID userId) {
        return ResponseEntity.ok(Map.of("count", notificacionService.contarNoLeidas(userId)));
    }

    @PatchMapping("/api/usuarios/me/notificaciones/{id}/leer")
    @Operation(summary = "Marcar una notificación como leída",
               security = @SecurityRequirement(name = "bearerAuth"))
    @ApiResponse(responseCode = "404", description = "Notificación no encontrada o no pertenece al usuario",
                 content = @Content)
    public ResponseEntity<NotificacionDto> marcarLeida(
            @PathVariable UUID id,
            @AuthenticationPrincipal UUID userId) {
        return notificacionService.marcarLeida(id, userId)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    @PatchMapping("/api/usuarios/me/notificaciones/leer-todas")
    @Operation(summary = "Marcar todas las notificaciones como leídas",
               security = @SecurityRequirement(name = "bearerAuth"))
    public ResponseEntity<Void> marcarTodasLeidas(
            @AuthenticationPrincipal UUID userId) {
        notificacionService.marcarTodasLeidas(userId);
        return ResponseEntity.noContent().build();
    }
}
