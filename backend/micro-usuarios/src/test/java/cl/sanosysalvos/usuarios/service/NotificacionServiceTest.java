package cl.sanosysalvos.usuarios.service;

import cl.sanosysalvos.usuarios.dto.NotificacionDto;
import cl.sanosysalvos.usuarios.model.Notificacion;
import cl.sanosysalvos.usuarios.repository.NotificacionRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class NotificacionServiceTest {

    @Mock
    private NotificacionRepository repo;

    @InjectMocks
    private NotificacionService notificacionService;

    // ── crearSiNoExiste ────────────────────────────────────────

    @Test
    void crearSiNoExiste_noExiste_guardarNotificacion() {
        UUID coincidenciaId = UUID.randomUUID();
        UUID usuarioId = UUID.randomUUID();
        when(repo.existsByCoincidenciaIdAndUsuarioId(coincidenciaId, usuarioId)).thenReturn(false);

        Notificacion saved = buildNotificacion(usuarioId, coincidenciaId);
        when(repo.save(any())).thenReturn(saved);

        notificacionService.crearSiNoExiste(coincidenciaId, usuarioId, "nueva_coincidencia", "Tienes una coincidencia");

        verify(repo).save(any(Notificacion.class));
    }

    @Test
    void crearSiNoExiste_yaExiste_noGuarda() {
        UUID coincidenciaId = UUID.randomUUID();
        UUID usuarioId = UUID.randomUUID();
        when(repo.existsByCoincidenciaIdAndUsuarioId(coincidenciaId, usuarioId)).thenReturn(true);

        notificacionService.crearSiNoExiste(coincidenciaId, usuarioId, "nueva_coincidencia", "Mensaje");

        verify(repo, never()).save(any());
    }

    // ── listarPorUsuario ───────────────────────────────────────

    @Test
    void listarPorUsuario_retornaListaDeDtos() {
        UUID usuarioId = UUID.randomUUID();
        Notificacion n = buildNotificacion(usuarioId, UUID.randomUUID());
        when(repo.findByUsuarioIdOrderByCreatedAtDesc(usuarioId)).thenReturn(List.of(n));

        List<NotificacionDto> result = notificacionService.listarPorUsuario(usuarioId);

        assertThat(result).hasSize(1);
        assertThat(result.get(0).getMensaje()).isEqualTo("Mascota encontrada cerca de tu zona");
    }

    @Test
    void listarPorUsuario_sinNotificaciones_retornaListaVacia() {
        UUID usuarioId = UUID.randomUUID();
        when(repo.findByUsuarioIdOrderByCreatedAtDesc(usuarioId)).thenReturn(List.of());

        List<NotificacionDto> result = notificacionService.listarPorUsuario(usuarioId);

        assertThat(result).isEmpty();
    }

    // ── contarNoLeidas ─────────────────────────────────────────

    @Test
    void contarNoLeidas_delegaAlRepositorio() {
        UUID usuarioId = UUID.randomUUID();
        when(repo.countByUsuarioIdAndLeidaFalse(usuarioId)).thenReturn(3L);

        long count = notificacionService.contarNoLeidas(usuarioId);

        assertThat(count).isEqualTo(3L);
    }

    // ── marcarLeida ────────────────────────────────────────────

    @Test
    void marcarLeida_propietario_marcaComoLeida() {
        UUID notificacionId = UUID.randomUUID();
        UUID usuarioId = UUID.randomUUID();

        Notificacion n = buildNotificacion(usuarioId, UUID.randomUUID());
        n.setId(notificacionId);

        when(repo.findById(notificacionId)).thenReturn(Optional.of(n));
        when(repo.save(n)).thenReturn(n);

        Optional<NotificacionDto> result = notificacionService.marcarLeida(notificacionId, usuarioId);

        assertThat(result).isPresent();
        assertThat(n.getLeida()).isTrue();
        verify(repo).save(n);
    }

    @Test
    void marcarLeida_otroUsuario_retornaEmpty() {
        UUID notificacionId = UUID.randomUUID();
        UUID propietario = UUID.randomUUID();
        UUID otroUsuario = UUID.randomUUID();

        Notificacion n = buildNotificacion(propietario, UUID.randomUUID());
        n.setId(notificacionId);

        when(repo.findById(notificacionId)).thenReturn(Optional.of(n));

        Optional<NotificacionDto> result = notificacionService.marcarLeida(notificacionId, otroUsuario);

        assertThat(result).isEmpty();
        verify(repo, never()).save(any());
    }

    @Test
    void marcarLeida_noExiste_retornaEmpty() {
        UUID notificacionId = UUID.randomUUID();
        when(repo.findById(notificacionId)).thenReturn(Optional.empty());

        Optional<NotificacionDto> result = notificacionService.marcarLeida(notificacionId, UUID.randomUUID());

        assertThat(result).isEmpty();
    }

    // ── marcarTodasLeidas ──────────────────────────────────────

    @Test
    void marcarTodasLeidas_delegaAlRepositorio() {
        UUID usuarioId = UUID.randomUUID();
        when(repo.marcarTodasLeidas(usuarioId)).thenReturn(5);

        notificacionService.marcarTodasLeidas(usuarioId);

        verify(repo).marcarTodasLeidas(usuarioId);
    }

    // ── helper ────────────────────────────────────────────────

    private Notificacion buildNotificacion(UUID usuarioId, UUID coincidenciaId) {
        return Notificacion.builder()
                .id(UUID.randomUUID())
                .usuarioId(usuarioId)
                .coincidenciaId(coincidenciaId)
                .tipo("nueva_coincidencia")
                .mensaje("Mascota encontrada cerca de tu zona")
                .build();
    }
}
