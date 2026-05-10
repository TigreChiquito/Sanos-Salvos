package cl.sanosysalvos.usuarios.dto;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class AuthResponseDto {

    private String token;

    @Builder.Default
    private String tokenType = "Bearer";

    private Long expiresIn;   // ms
    private UsuarioDto usuario;
}
