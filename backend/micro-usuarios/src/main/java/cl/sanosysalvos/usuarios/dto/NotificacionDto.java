package cl.sanosysalvos.usuarios.dto;

import cl.sanosysalvos.usuarios.model.Notificacion;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;
import lombok.Data;

import java.time.OffsetDateTime;
import java.util.UUID;

@Data
@Builder
@Schema(description = "Notificación del sistema para el usuario")
public class NotificacionDto {

    @Schema(description = "UUID de la notificación")
    private UUID id;

    @Schema(description = "Tipo de notificación", example = "nueva_coincidencia")
    private String tipo;

    @Schema(description = "Mensaje descriptivo", example = "Tu reporte tiene una posible coincidencia (78% de similitud).")
    private String mensaje;

    @Schema(description = "Si el usuario ya la leyó")
    private boolean leida;

    @Schema(description = "UUID de la coincidencia asociada (nullable)")
    private UUID coincidenciaId;

    @Schema(description = "Fecha y hora de creación")
    private OffsetDateTime createdAt;

    public static NotificacionDto from(Notificacion n) {
        return NotificacionDto.builder()
                .id(n.getId())
                .tipo(n.getTipo())
                .mensaje(n.getMensaje())
                .leida(Boolean.TRUE.equals(n.getLeida()))
                .coincidenciaId(n.getCoincidenciaId())
                .createdAt(n.getCreatedAt())
                .build();
    }
}
