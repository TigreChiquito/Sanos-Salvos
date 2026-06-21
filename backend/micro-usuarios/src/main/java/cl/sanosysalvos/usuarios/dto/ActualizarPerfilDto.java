package cl.sanosysalvos.usuarios.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
@Schema(description = "Campos editables del perfil de usuario")
public class ActualizarPerfilDto {

    @Size(max = 20, message = "Máximo 20 caracteres")
    @Pattern(regexp = "^[+\\d\\s\\-()]*$", message = "Formato de teléfono inválido")
    @Schema(description = "Teléfono de contacto", example = "+56 9 1234 5678")
    private String telefono;

    @Schema(description = "Recibir notificaciones por correo")
    private Boolean notifEmail;

    @Schema(description = "Recibir notificaciones del sistema")
    private Boolean notifSistema;
}
