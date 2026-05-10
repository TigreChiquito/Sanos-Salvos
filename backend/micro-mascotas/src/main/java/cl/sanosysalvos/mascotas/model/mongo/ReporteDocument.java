package cl.sanosysalvos.mascotas.model.mongo;

import lombok.*;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.geo.GeoJsonPoint;
import org.springframework.data.mongodb.core.index.GeoSpatialIndexType;
import org.springframework.data.mongodb.core.index.GeoSpatialIndexed;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.OffsetDateTime;
import java.util.List;

/**
 * Documento MongoDB que representa un reporte en la capa de lectura (CQRS).
 *
 * Este documento es una proyección desnormalizada de la tabla `reportes` de
 * PostgreSQL, optimizada para las queries del mapa y la búsqueda de mascotas.
 *
 * Se popula y actualiza vía el consumidor Kafka (ReporteSyncConsumer) que
 * escucha los eventos publicados por ReporteService al escribir en PostgreSQL.
 *
 * El índice geoespacial 2dsphere permite queries eficientes por bounds del mapa
 * y por proximidad (radio de búsqueda del Motor de Coincidencias).
 */
@Document(collection = "reportes")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ReporteDocument {

    @Id
    private String id;  // mismo UUID que en PostgreSQL

    // ── Clasificación ──────────────────────────────────────────
    @Indexed
    private String tipo;    // "perdido" | "encontrado"

    @Indexed
    private String animal;  // "perro" | "gato" | "otro"

    @Indexed
    private String estado;  // "activo" | "resuelto" | "eliminado"

    // ── Atributos de la mascota ────────────────────────────────
    private String nombre;
    private String raza;
    private String color;
    private String tamano;
    private String descripcion;

    // ── Ubicación (GeoJSON para queries espaciales) ────────────
    @GeoSpatialIndexed(type = GeoSpatialIndexType.GEO_2DSPHERE)
    private GeoJsonPoint ubicacion;  // [lng, lat] (orden GeoJSON estándar)

    private Double lat;
    private Double lng;
    private String zona;

    // ── Fotos (desnormalizadas para evitar joins) ──────────────
    private List<FotoEmbedded> fotos;

    // ── Datos del autor (desnormalizados) ─────────────────────
    private String usuarioId;
    private String usuarioNombre;
    private String usuarioFotoPerfil;

    // ── Auditoría ──────────────────────────────────────────────
    private OffsetDateTime createdAt;
    private OffsetDateTime updatedAt;

    // ── Foto embebida ──────────────────────────────────────────
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class FotoEmbedded {
        private String id;
        private String url;
        private Integer orden;
    }
}
