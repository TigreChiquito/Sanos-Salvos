package cl.sanosysalvos.usuarios.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;
import lombok.Data;

@Data
@Builder
@Schema(description = "Respuesta de autenticación con JWT y datos del usuario")
public class AuthResponseDto {

    @Schema(description = "JWT de acceso", example = "eyJhbGciOiJIUzI1NiJ9...")
    private String token;

    @Schema(description = "Tipo de token", example = "Bearer")
    @Builder.Default
    private String tokenType = "Bearer";

    @Schema(description = "Tiempo de expiración en milisegundos", example = "86400000")
    private Long expiresIn;

    @Schema(description = "Datos del usuario autenticado")
    private UsuarioDto usuario;
}
