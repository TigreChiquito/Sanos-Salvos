package cl.sanosysalvos.mascotas.controller;

import cl.sanosysalvos.mascotas.dto.ReporteRequestDto;
import cl.sanosysalvos.mascotas.dto.ReporteResponseDto;
import cl.sanosysalvos.mascotas.service.ReporteService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Endpoints de gestión de reportes de mascotas.
 *
 * GET    /api/reportes              → listado con filtros (público)
 * GET    /api/reportes/{id}         → detalle de un reporte (público)
 * POST   /api/reportes              → crear reporte + fotos (requiere JWT)
 * PATCH  /api/reportes/{id}/estado  → cambiar estado (requiere JWT, solo autor)
 * DELETE /api/reportes/{id}         → eliminar reporte (requiere JWT, solo autor)
 */
@Slf4j
@RestController
@RequestMapping("/api/reportes")
@RequiredArgsConstructor
@Tag(name = "Reportes", description = "Gestión de reportes de mascotas perdidas y encontradas")
public class ReporteController {

    private final ReporteService reporteService;

    /**
     * Lista reportes activos con filtros opcionales.
     * Soporta filtrado por tipo, animal y bounds del mapa.
     *
     * Ejemplo mapa: GET /api/reportes?latMin=-33.6&latMax=-33.3&lngMin=-70.8&lngMax=-70.5
     * Ejemplo filtro: GET /api/reportes?tipo=perdido&animal=gato
     */
    @GetMapping
    @Operation(summary = "Listar reportes activos")
    public ResponseEntity<List<ReporteResponseDto>> listar(
            @RequestParam(required = false) String tipo,
            @RequestParam(required = false) String animal,
            @RequestParam(required = false) Double latMin,
            @RequestParam(required = false) Double latMax,
            @RequestParam(required = false) Double lngMin,
            @RequestParam(required = false) Double lngMax) {

        List<ReporteResponseDto> reportes =
                reporteService.listar(tipo, animal, latMin, latMax, lngMin, lngMax);
        return ResponseEntity.ok(reportes);
    }

    /**
     * Detalle de un reporte por ID.
     */
    @GetMapping("/{id}")
    @Operation(summary = "Detalle de un reporte")
    public ResponseEntity<ReporteResponseDto> obtener(@PathVariable UUID id) {
        return reporteService.obtener(id)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    /**
     * Crea un nuevo reporte con fotos.
     * Request: multipart/form-data
     *   - datos: JSON con los atributos del reporte (ReporteRequestDto)
     *   - fotos: hasta 5 archivos de imagen
     */
    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @Operation(summary = "Crear reporte",
               security = @SecurityRequirement(name = "bearerAuth"))
    public ResponseEntity<ReporteResponseDto> crear(
            @RequestPart("datos") @Valid ReporteRequestDto dto,
            @RequestPart(value = "fotos", required = false) List<MultipartFile> fotos,
            @AuthenticationPrincipal UUID usuarioId) {

        if (fotos != null && fotos.size() > 5) {
            return ResponseEntity.badRequest().build();
        }

        ReporteResponseDto creado = reporteService.crear(dto, fotos, usuarioId);
        return ResponseEntity.status(HttpStatus.CREATED).body(creado);
    }

    /**
     * Actualiza el estado de un reporte.
     * Body: { "estado": "resuelto" }
     * Estados válidos: "activo" | "resuelto"
     */
    @PatchMapping("/{id}/estado")
    @Operation(summary = "Actualizar estado del reporte",
               security = @SecurityRequirement(name = "bearerAuth"))
    public ResponseEntity<ReporteResponseDto> actualizarEstado(
            @PathVariable UUID id,
            @RequestBody Map<String, String> body,
            @AuthenticationPrincipal UUID usuarioId) {

        String nuevoEstado = body.get("estado");
        if (nuevoEstado == null || !List.of("activo", "resuelto").contains(nuevoEstado)) {
            return ResponseEntity.badRequest().build();
        }

        try {
            return reporteService.actualizarEstado(id, nuevoEstado, usuarioId)
                    .map(ResponseEntity::ok)
                    .orElse(ResponseEntity.notFound().build());
        } catch (SecurityException e) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
        }
    }

    /**
     * Elimina un reporte (soft delete → estado = "eliminado").
     * Solo el autor puede hacerlo.
     */
    @DeleteMapping("/{id}")
    @Operation(summary = "Eliminar reporte",
               security = @SecurityRequirement(name = "bearerAuth"))
    public ResponseEntity<Void> eliminar(
            @PathVariable UUID id,
            @AuthenticationPrincipal UUID usuarioId) {

        try {
            boolean eliminado = reporteService.eliminar(id, usuarioId);
            return eliminado
                    ? ResponseEntity.noContent().build()
                    : ResponseEntity.notFound().build();
        } catch (SecurityException e) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
        }
    }
}
