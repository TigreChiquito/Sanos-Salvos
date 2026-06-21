package cl.sanosysalvos.mascotas.model.pg;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;

import java.time.OffsetDateTime;
import java.util.UUID;

@Entity
@Table(name = "fotos")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Foto {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "reporte_id", nullable = false)
    @ToString.Exclude
    private Reporte reporte;

    // URL pública/presignada para acceso desde el frontend
    @Column(nullable = false)
    private String url;

    // Ruta interna en el bucket de MinIO
    @Column(name = "bucket_key", nullable = false)
    private String bucketKey;

    // Orden de display (0 = foto principal)
    @Builder.Default
    private Integer orden = 0;

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private OffsetDateTime createdAt;
}
