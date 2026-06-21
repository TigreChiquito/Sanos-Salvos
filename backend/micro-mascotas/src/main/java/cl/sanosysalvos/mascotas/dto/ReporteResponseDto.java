package cl.sanosysalvos.mascotas.dto;

import cl.sanosysalvos.mascotas.model.mongo.ReporteDocument;
import cl.sanosysalvos.mascotas.model.pg.Foto;
import cl.sanosysalvos.mascotas.model.pg.Reporte;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;

@Data
@Builder
@Schema(description = "Reporte de mascota perdida o encontrada")
public class ReporteResponseDto {

    @Schema(description = "UUID del reporte", example = "550e8400-e29b-41d4-a716-446655440000")
    private UUID id;

    @Schema(description = "Tipo de reporte", allowableValues = {"perdido", "encontrado"}, example = "perdido")
    private String tipo;

    @Schema(description = "Tipo de animal", allowableValues = {"perro", "gato", "otro"}, example = "perro")
    private String animal;

    @Schema(description = "Estado del reporte", allowableValues = {"activo", "resuelto", "eliminado"}, example = "activo")
    private String estado;

    @Schema(description = "Nombre de la mascota", example = "Firulais")
    private String nombre;

    @Schema(description = "Raza", example = "Labrador")
    private String raza;

    @Schema(description = "Color del pelaje", example = "café con manchas blancas")
    private String color;

    @Schema(description = "Tamaño", allowableValues = {"pequeño", "mediano", "grande"}, example = "mediano")
    private String tamano;

    @Schema(description = "Descripción adicional")
    private String descripcion;

    @Schema(description = "Latitud", example = "-33.4489")
    private BigDecimal lat;

    @Schema(description = "Longitud", example = "-70.6693")
    private BigDecimal lng;

    @Schema(description = "Zona o comuna", example = "Providencia")
    private String zona;

    @Schema(description = "UUID del usuario que creó el reporte")
    private UUID usuarioId;

    @Schema(description = "Fotos de la mascota")
    private List<FotoDto> fotos;

    @Schema(description = "Fecha y hora de creación")
    private OffsetDateTime createdAt;

    @Schema(description = "Fecha y hora de última actualización")
    private OffsetDateTime updatedAt;

    /** Convierte entidad JPA → DTO (para respuestas de escritura) */
    public static ReporteResponseDto from(Reporte r) {
        return ReporteResponseDto.builder()
                .id(r.getId())
                .tipo(r.getTipo())
                .animal(r.getAnimal())
                .estado(r.getEstado())
                .nombre(r.getNombre())
                .raza(r.getRaza())
                .color(r.getColor())
                .tamano(r.getTamano())
                .descripcion(r.getDescripcion())
                .lat(r.getLat())
                .lng(r.getLng())
                .zona(r.getZona())
                .usuarioId(r.getUsuarioId())
                .fotos(r.getFotos().stream().map(FotoDto::from).toList())
                .createdAt(r.getCreatedAt())
                .updatedAt(r.getUpdatedAt())
                .build();
    }

    /** Convierte documento MongoDB → DTO (para queries de lectura) */
    public static ReporteResponseDto from(ReporteDocument d) {
        List<FotoDto> fotos = d.getFotos() == null ? List.of() :
                d.getFotos().stream()
                        .map(f -> FotoDto.builder()
                                .id(UUID.fromString(f.getId()))
                                .url(f.getUrl())
                                .orden(f.getOrden())
                                .build())
                        .toList();

        return ReporteResponseDto.builder()
                .id(UUID.fromString(d.getId()))
                .tipo(d.getTipo())
                .animal(d.getAnimal())
                .estado(d.getEstado())
                .nombre(d.getNombre())
                .raza(d.getRaza())
                .color(d.getColor())
                .tamano(d.getTamano())
                .descripcion(d.getDescripcion())
                .lat(d.getLat() != null ? BigDecimal.valueOf(d.getLat()) : null)
                .lng(d.getLng() != null ? BigDecimal.valueOf(d.getLng()) : null)
                .zona(d.getZona())
                .usuarioId(d.getUsuarioId() != null ? UUID.fromString(d.getUsuarioId()) : null)
                .fotos(fotos)
                .createdAt(d.getCreatedAt() != null ? d.getCreatedAt().atOffset(ZoneOffset.UTC) : null)
                .updatedAt(d.getUpdatedAt() != null ? d.getUpdatedAt().atOffset(ZoneOffset.UTC) : null)
                .build();
    }

    // ── DTO anidado para fotos ─────────────────────────────────

    @Data
    @Builder
    @Schema(description = "Foto de la mascota almacenada en MinIO")
    public static class FotoDto {

        @Schema(description = "UUID de la foto")
        private UUID id;

        @Schema(description = "URL pública de la imagen")
        private String url;

        @Schema(description = "Orden de la foto en la galería", example = "0")
        private Integer orden;

        public static FotoDto from(Foto f) {
            return FotoDto.builder()
                    .id(f.getId())
                    .url(f.getUrl())
                    .orden(f.getOrden())
                    .build();
        }
    }
}
