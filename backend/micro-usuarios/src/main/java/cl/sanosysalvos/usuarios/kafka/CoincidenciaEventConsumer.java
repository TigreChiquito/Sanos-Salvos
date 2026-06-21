package cl.sanosysalvos.usuarios.kafka;

import cl.sanosysalvos.usuarios.repository.UsuarioRepository;
import cl.sanosysalvos.usuarios.service.NotificacionService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.UUID;

/**
 * Escucha el tópico ss.coincidencias.found publicado por micro-coincidencias
 * y crea notificaciones en PostgreSQL para los usuarios afectados (si tienen
 * notifSistema habilitado).
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class CoincidenciaEventConsumer {

    private final NotificacionService notificacionService;
    private final UsuarioRepository   usuarioRepository;

    @KafkaListener(
        topics   = "ss.coincidencias.found",
        groupId  = "${spring.kafka.consumer.group-id}",
        containerFactory = "kafkaListenerContainerFactory"
    )
    public void onCoincidencia(Map<String, Object> payload) {
        try {
            String eventType = (String) payload.get("eventType");
            if (!"COINCIDENCIA_ENCONTRADA".equals(eventType)) return;

            UUID coincidenciaId     = UUID.fromString((String) payload.get("coincidenciaId"));
            UUID usuarioPerdidoId   = UUID.fromString((String) payload.get("usuarioPerdidoId"));
            UUID usuarioEncontradoId = UUID.fromString((String) payload.get("usuarioEncontradoId"));

            double scoreTotal = ((Number) payload.get("scoreTotal")).doubleValue();
            long   pct        = Math.round(scoreTotal * 100);
            String mensaje    = String.format(
                "¡Tu reporte tiene una posible coincidencia con un %d%% de similitud! Ingresa a la app para verla.",
                pct
            );

            notificarSiActivo(usuarioPerdidoId,    coincidenciaId, mensaje);
            notificarSiActivo(usuarioEncontradoId, coincidenciaId, mensaje);

        } catch (Exception e) {
            log.error("Error procesando evento de coincidencia: {}", e.getMessage(), e);
        }
    }

    private void notificarSiActivo(UUID usuarioId, UUID coincidenciaId, String mensaje) {
        usuarioRepository.findById(usuarioId).ifPresentOrElse(
            u -> {
                if (Boolean.TRUE.equals(u.getNotifSistema())) {
                    notificacionService.crearSiNoExiste(
                        coincidenciaId, usuarioId, "nueva_coincidencia", mensaje
                    );
                } else {
                    log.debug("Usuario {} tiene notifSistema=false — omitiendo notificación", usuarioId);
                }
            },
            () -> log.warn("Usuario {} no encontrado al intentar notificar", usuarioId)
        );
    }
}
