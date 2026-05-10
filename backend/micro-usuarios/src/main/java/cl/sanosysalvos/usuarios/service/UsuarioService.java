package cl.sanosysalvos.usuarios.service;

import cl.sanosysalvos.usuarios.kafka.UsuarioEventProducer;
import cl.sanosysalvos.usuarios.model.Usuario;
import cl.sanosysalvos.usuarios.repository.UsuarioRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class UsuarioService {

    private final UsuarioRepository usuarioRepository;
    private final UsuarioEventProducer eventProducer;

    /**
     * Busca un usuario por googleId. Si no existe, lo crea.
     * Si existe pero su perfil cambió (nombre, foto), lo actualiza.
     *
     * Llamado desde OAuth2SuccessHandler tras cada login con Google.
     */
    @Transactional
    public Usuario findOrCreate(String googleId, String email,
                                String nombre, String apellido, String fotoPerfil) {

        Optional<Usuario> existing = usuarioRepository.findByGoogleId(googleId);

        if (existing.isPresent()) {
            return updateIfChanged(existing.get(), nombre, apellido, fotoPerfil);
        }

        return createNew(googleId, email, nombre, apellido, fotoPerfil);
    }

    /** Busca un usuario por ID */
    @Transactional(readOnly = true)
    public Optional<Usuario> findById(UUID id) {
        return usuarioRepository.findById(id);
    }

    // ── Privado ────────────────────────────────────────────────

    private Usuario createNew(String googleId, String email,
                               String nombre, String apellido, String fotoPerfil) {
        Usuario nuevo = Usuario.builder()
                .googleId(googleId)
                .email(email)
                .nombre(nombre != null ? nombre : "")
                .apellido(apellido != null ? apellido : "")
                .fotoPerfilUrl(fotoPerfil)
                .activo(true)
                .build();

        Usuario saved = usuarioRepository.save(nuevo);
        log.info("Nuevo usuario registrado: {} ({})", saved.getEmail(), saved.getId());

        // Publicar evento en Kafka
        eventProducer.publishCreated(saved);

        return saved;
    }

    private Usuario updateIfChanged(Usuario usuario, String nombre,
                                     String apellido, String fotoPerfil) {
        boolean changed = false;

        if (nombre != null && !nombre.equals(usuario.getNombre())) {
            usuario.setNombre(nombre);
            changed = true;
        }
        if (apellido != null && !apellido.equals(usuario.getApellido())) {
            usuario.setApellido(apellido);
            changed = true;
        }
        if (fotoPerfil != null && !fotoPerfil.equals(usuario.getFotoPerfilUrl())) {
            usuario.setFotoPerfilUrl(fotoPerfil);
            changed = true;
        }

        if (changed) {
            Usuario updated = usuarioRepository.save(usuario);
            log.debug("Perfil actualizado para usuario: {}", updated.getId());
            eventProducer.publishUpdated(updated);
            return updated;
        }

        return usuario;
    }
}
