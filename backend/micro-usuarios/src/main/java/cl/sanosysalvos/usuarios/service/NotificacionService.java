package cl.sanosysalvos.usuarios.service;

import cl.sanosysalvos.usuarios.dto.NotificacionDto;
import cl.sanosysalvos.usuarios.model.Notificacion;
import cl.sanosysalvos.usuarios.repository.NotificacionRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class NotificacionService {

    private final NotificacionRepository repo;

    /**
     * Crea una notificación solo si aún no existe una para esta
     * (coincidenciaId, usuarioId), garantizando idempotencia ante
     * re-entregas del mismo evento Kafka.
     */
    @Transactional
    public void crearSiNoExiste(UUID coincidenciaId, UUID usuarioId, String tipo, String mensaje) {
        if (repo.existsByCoincidenciaIdAndUsuarioId(coincidenciaId, usuarioId)) {
            log.debug("Notificación ya existe — coincidencia {} usuario {}", coincidenciaId, usuarioId);
            return;
        }
        Notificacion n = Notificacion.builder()
                .usuarioId(usuarioId)
                .coincidenciaId(coincidenciaId)
                .tipo(tipo)
                .mensaje(mensaje)
                .build();
        repo.save(n);
        log.info("Notificación creada para usuario {} — coincidencia {}", usuarioId, coincidenciaId);
    }

    @Transactional(readOnly = true)
    public List<NotificacionDto> listarPorUsuario(UUID usuarioId) {
        return repo.findByUsuarioIdOrderByCreatedAtDesc(usuarioId)
                .stream()
                .map(NotificacionDto::from)
                .toList();
    }

    @Transactional(readOnly = true)
    public long contarNoLeidas(UUID usuarioId) {
        return repo.countByUsuarioIdAndLeidaFalse(usuarioId);
    }

    @Transactional
    public Optional<NotificacionDto> marcarLeida(UUID notificacionId, UUID usuarioId) {
        return repo.findById(notificacionId)
                .filter(n -> n.getUsuarioId().equals(usuarioId))
                .map(n -> {
                    n.setLeida(true);
                    return NotificacionDto.from(repo.save(n));
                });
    }

    @Transactional
    public void marcarTodasLeidas(UUID usuarioId) {
        int updated = repo.marcarTodasLeidas(usuarioId);
        log.debug("Marcadas {} notificaciones como leídas para usuario {}", updated, usuarioId);
    }
}
