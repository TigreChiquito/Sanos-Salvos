package cl.sanosysalvos.mascotas.dto;

import cl.sanosysalvos.mascotas.model.mongo.ReporteDocument;
import cl.sanosysalvos.mascotas.model.pg.Foto;
import cl.sanosysalvos.mascotas.model.pg.Reporte;
import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;

@Data
@Builder
public class ReporteResponseDto {

    private UUID id;
    private String tipo;
    private String animal;
    private String estado;
    private String nombre;
    private String raza;
    private String color;
    private String tamano;
    private String descripcion;
    private BigDecimal lat;
    private BigDecimal lng;
    private String zona;
    private UUID usuarioId;
    private List<FotoDto> fotos;
    private OffsetDateTime createdAt;
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
    public static class FotoDto {
        private UUID id;
        private String url;
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
