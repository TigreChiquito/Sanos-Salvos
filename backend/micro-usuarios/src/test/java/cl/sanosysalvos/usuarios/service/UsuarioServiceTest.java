package cl.sanosysalvos.usuarios.service;

import cl.sanosysalvos.usuarios.dto.ActualizarPerfilDto;
import cl.sanosysalvos.usuarios.kafka.UsuarioEventProducer;
import cl.sanosysalvos.usuarios.model.Usuario;
import cl.sanosysalvos.usuarios.repository.UsuarioRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class UsuarioServiceTest {

    @Mock
    private UsuarioRepository usuarioRepository;

    @Mock
    private UsuarioEventProducer eventProducer;

    @InjectMocks
    private UsuarioService usuarioService;

    // ── findOrCreate ───────────────────────────────────────────

    @Test
    void findOrCreate_usuarioNuevo_creaYPublicaEvento() {
        when(usuarioRepository.findByGoogleId("g-001")).thenReturn(Optional.empty());

        Usuario saved = buildUsuario("g-001", "juan@test.com", "Juan", "Perez");
        when(usuarioRepository.save(any())).thenReturn(saved);

        Usuario result = usuarioService.findOrCreate("g-001", "juan@test.com", "Juan", "Perez", null);

        assertThat(result.getEmail()).isEqualTo("juan@test.com");
        verify(eventProducer).publishCreated(saved);
        verify(usuarioRepository).save(any());
    }

    @Test
    void findOrCreate_usuarioExistenteSinCambios_retornaExistente() {
        Usuario existing = buildUsuario("g-002", "ana@test.com", "Ana", "Lopez");
        when(usuarioRepository.findByGoogleId("g-002")).thenReturn(Optional.of(existing));

        Usuario result = usuarioService.findOrCreate("g-002", "ana@test.com", "Ana", "Lopez", null);

        assertThat(result).isSameAs(existing);
        verify(usuarioRepository, never()).save(any());
        verify(eventProducer, never()).publishUpdated(any());
    }

    @Test
    void findOrCreate_usuarioExistenteConCambioNombre_actualizaYPublica() {
        Usuario existing = buildUsuario("g-003", "pedro@test.com", "Pedro", "Garcia");
        when(usuarioRepository.findByGoogleId("g-003")).thenReturn(Optional.of(existing));

        Usuario updated = buildUsuario("g-003", "pedro@test.com", "Pedro A.", "Garcia");
        when(usuarioRepository.save(existing)).thenReturn(updated);

        Usuario result = usuarioService.findOrCreate("g-003", "pedro@test.com", "Pedro A.", "Garcia", null);

        assertThat(result.getNombre()).isEqualTo("Pedro A.");
        verify(eventProducer).publishUpdated(updated);
    }

    @Test
    void findOrCreate_usuarioExistenteConCambioFoto_actualizaYPublica() {
        Usuario existing = buildUsuario("g-004", "lucia@test.com", "Lucia", "Torres");
        existing.setFotoPerfilUrl("http://old.jpg");
        when(usuarioRepository.findByGoogleId("g-004")).thenReturn(Optional.of(existing));
        when(usuarioRepository.save(existing)).thenReturn(existing);

        usuarioService.findOrCreate("g-004", "lucia@test.com", "Lucia", "Torres", "http://new.jpg");

        verify(eventProducer).publishUpdated(any());
    }

    @Test
    void findOrCreate_nombreNull_usaVacio() {
        when(usuarioRepository.findByGoogleId("g-005")).thenReturn(Optional.empty());

        Usuario saved = buildUsuario("g-005", "x@test.com", "", "");
        when(usuarioRepository.save(any())).thenReturn(saved);

        usuarioService.findOrCreate("g-005", "x@test.com", null, null, null);

        verify(usuarioRepository).save(any());
    }

    // ── findById ───────────────────────────────────────────────

    @Test
    void findById_delegaAlRepositorio() {
        UUID id = UUID.randomUUID();
        Usuario u = buildUsuario("g-006", "b@test.com", "B", "B");
        u = Usuario.builder().id(id).nombre("B").apellido("B")
                   .email("b@test.com").googleId("g-006").build();
        when(usuarioRepository.findById(id)).thenReturn(Optional.of(u));

        Optional<Usuario> result = usuarioService.findById(id);

        assertThat(result).isPresent();
        assertThat(result.get().getId()).isEqualTo(id);
    }

    // ── actualizarPerfil ───────────────────────────────────────

    @Test
    void actualizarPerfil_encontrado_actualizaCampos() {
        UUID id = UUID.randomUUID();
        Usuario u = buildUsuario("g-007", "c@test.com", "C", "C");
        when(usuarioRepository.findById(id)).thenReturn(Optional.of(u));
        when(usuarioRepository.save(u)).thenReturn(u);

        ActualizarPerfilDto dto = new ActualizarPerfilDto();
        dto.setTelefono("+56 9 1234 5678");
        dto.setNotifEmail(false);
        dto.setNotifSistema(false);

        Optional<Usuario> result = usuarioService.actualizarPerfil(id, dto);

        assertThat(result).isPresent();
        assertThat(u.getTelefono()).isEqualTo("+56 9 1234 5678");
        assertThat(u.getNotifEmail()).isFalse();
        assertThat(u.getNotifSistema()).isFalse();
    }

    @Test
    void actualizarPerfil_telefonoBlank_ponNull() {
        UUID id = UUID.randomUUID();
        Usuario u = buildUsuario("g-008", "d@test.com", "D", "D");
        when(usuarioRepository.findById(id)).thenReturn(Optional.of(u));
        when(usuarioRepository.save(u)).thenReturn(u);

        ActualizarPerfilDto dto = new ActualizarPerfilDto();
        dto.setTelefono("   ");

        usuarioService.actualizarPerfil(id, dto);

        assertThat(u.getTelefono()).isNull();
    }

    @Test
    void actualizarPerfil_usuarioNoEncontrado_retornaEmpty() {
        UUID id = UUID.randomUUID();
        when(usuarioRepository.findById(id)).thenReturn(Optional.empty());

        ActualizarPerfilDto dto = new ActualizarPerfilDto();
        dto.setTelefono("+56 9 0000 0000");

        Optional<Usuario> result = usuarioService.actualizarPerfil(id, dto);

        assertThat(result).isEmpty();
        verify(usuarioRepository, never()).save(any());
    }

    // ── helper ────────────────────────────────────────────────

    private Usuario buildUsuario(String googleId, String email, String nombre, String apellido) {
        return Usuario.builder()
                .id(UUID.randomUUID())
                .googleId(googleId)
                .email(email)
                .nombre(nombre)
                .apellido(apellido)
                .build();
    }
}
