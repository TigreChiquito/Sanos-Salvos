package cl.sanosysalvos.usuarios.dto;

import cl.sanosysalvos.usuarios.model.Usuario;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;
import lombok.Data;

import java.util.UUID;

@Data
@Builder
@Schema(description = "Perfil público de un usuario")
public class UsuarioDto {

    @Schema(description = "UUID del usuario", example = "550e8400-e29b-41d4-a716-446655440000")
    private UUID id;

    @Schema(description = "Nombre de pila", example = "Juan")
    private String nombre;

    @Schema(description = "Apellido", example = "Pérez")
    private String apellido;

    @Schema(description = "Correo electrónico de la cuenta Google", example = "juan@gmail.com")
    private String email;

    @Schema(description = "URL de la foto de perfil de Google")
    private String fotoPerfilUrl;

    @Schema(description = "Iniciales para mostrar en el avatar", example = "JP")
    private String initials;

    @Schema(description = "Teléfono de contacto", example = "+56 9 1234 5678")
    private String telefono;

    @Schema(description = "Recibir notificaciones por correo")
    private Boolean notifEmail;

    @Schema(description = "Recibir notificaciones del sistema")
    private Boolean notifSistema;

    /** Convierte una entidad Usuario a DTO */
    public static UsuarioDto from(Usuario u) {
        return UsuarioDto.builder()
                .id(u.getId())
                .nombre(u.getNombre())
                .apellido(u.getApellido())
                .email(u.getEmail())
                .fotoPerfilUrl(u.getFotoPerfilUrl())
                .initials(u.getInitials())
                .telefono(u.getTelefono())
                .notifEmail(u.getNotifEmail() != null ? u.getNotifEmail() : true)
                .notifSistema(u.getNotifSistema() != null ? u.getNotifSistema() : true)
                .build();
    }
}
