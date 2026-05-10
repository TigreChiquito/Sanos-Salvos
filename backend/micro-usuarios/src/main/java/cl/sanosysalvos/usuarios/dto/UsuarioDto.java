package cl.sanosysalvos.usuarios.dto;

import cl.sanosysalvos.usuarios.model.Usuario;
import lombok.Builder;
import lombok.Data;

import java.util.UUID;

@Data
@Builder
public class UsuarioDto {

    private UUID id;
    private String nombre;
    private String apellido;
    private String email;
    private String fotoPerfilUrl;
    private String initials;

    /** Convierte una entidad Usuario a DTO */
    public static UsuarioDto from(Usuario u) {
        return UsuarioDto.builder()
                .id(u.getId())
                .nombre(u.getNombre())
                .apellido(u.getApellido())
                .email(u.getEmail())
                .fotoPerfilUrl(u.getFotoPerfilUrl())
                .initials(u.getInitials())
                .build();
    }
}
