package cl.sanosysalvos.usuarios.model;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.OffsetDateTime;
import java.util.UUID;

@Entity
@Table(name = "usuarios")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Usuario {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(nullable = false, length = 100)
    private String nombre;

    @Column(nullable = false, length = 100)
    private String apellido;

    @Column(unique = true, nullable = false, length = 255)
    private String email;

    // Sub del JWT de Google — identificador único de la cuenta Google
    @Column(name = "google_id", unique = true, nullable = false, length = 255)
    private String googleId;

    @Column(name = "foto_perfil_url")
    private String fotoPerfilUrl;

    @Builder.Default
    private Boolean activo = true;

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private OffsetDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at")
    private OffsetDateTime updatedAt;

    // ── Helpers ────────────────────────────────────────────────

    /** Retorna las iniciales del usuario. Ej: "Carlos Méndez" → "CM" */
    @Transient
    public String getInitials() {
        StringBuilder sb = new StringBuilder();
        if (nombre != null && !nombre.isBlank()) sb.append(nombre.charAt(0));
        if (apellido != null && !apellido.isBlank()) sb.append(apellido.charAt(0));
        return sb.toString().toUpperCase();
    }

    /** Retorna el nombre completo */
    @Transient
    public String getNombreCompleto() {
        return nombre + " " + apellido;
    }
}
