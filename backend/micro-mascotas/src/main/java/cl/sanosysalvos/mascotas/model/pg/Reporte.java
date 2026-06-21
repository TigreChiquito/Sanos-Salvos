package cl.sanosysalvos.mascotas.model.pg;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Entidad JPA que mapea la tabla `reportes` en PostgreSQL.
 * Esta es la fuente de verdad (escritura en CQRS).
 *
 * El campo `embedding` es gestionado exclusivamente por Micro 3
 * (Motor de Coincidencias) después de procesar texto e imágenes.
 * No se incluye aquí para no acoplar Micro 2 con lógica de ML.
 */
@Entity
@Table(name = "reportes")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Reporte {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(nullable = false, length = 20)
    private String tipo;        // "perdido" | "encontrado"

    @Column(nullable = false, length = 20)
    private String animal;      // "perro" | "gato" | "otro"

    @Builder.Default
    @Column(nullable = false, length = 20)
    private String estado = "activo";  // "activo" | "resuelto" | "eliminado"

    // ── Atributos de la mascota ────────────────────────────────
    @Column(length = 100)
    private String nombre;

    @Column(length = 100)
    private String raza;

    @Column(length = 100)
    private String color;

    @Column(length = 20)
    private String tamano;      // "pequeño" | "mediano" | "grande"

    @Column(columnDefinition = "TEXT")
    private String descripcion;

    // ── Ubicación ──────────────────────────────────────────────
    @Column(nullable = false, precision = 10, scale = 8)
    private BigDecimal lat;

    @Column(nullable = false, precision = 11, scale = 8)
    private BigDecimal lng;

    @Column(length = 255)
    private String zona;        // texto derivado de geocodificación inversa

    // ── Relaciones ─────────────────────────────────────────────
    @Column(name = "usuario_id", nullable = false)
    private UUID usuarioId;

    @OneToMany(mappedBy = "reporte", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.LAZY)
    @Builder.Default
    private List<Foto> fotos = new ArrayList<>();

    // ── Auditoría ──────────────────────────────────────────────
    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private OffsetDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at")
    private OffsetDateTime updatedAt;
}
