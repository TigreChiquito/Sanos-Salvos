package cl.sanosysalvos.usuarios.security;

import cl.sanosysalvos.usuarios.model.Usuario;
import cl.sanosysalvos.usuarios.service.UsuarioService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.security.web.authentication.SimpleUrlAuthenticationSuccessHandler;
import org.springframework.stereotype.Component;
import org.springframework.web.util.UriComponentsBuilder;

import java.io.IOException;

/**
 * Handler ejecutado por Spring Security tras un login OAuth2 exitoso con Google.
 *
 * Flujo:
 *   1. Google autentica al usuario y redirige a /login/oauth2/code/google
 *   2. Spring Security procesa el callback y llama a este handler
 *   3. Se crea o actualiza el usuario en PostgreSQL
 *   4. Se genera un JWT
 *   5. Se redirige al frontend con el token en el query param ?token=...
 *
 * El frontend (acceder.astro) leerá el token, lo almacenará y redirigirá al mapa.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class OAuth2SuccessHandler extends SimpleUrlAuthenticationSuccessHandler {

    private final UsuarioService usuarioService;
    private final JwtTokenProvider jwtTokenProvider;

    @Value("${app.oauth2.redirect-uri}")
    private String frontendRedirectUri;

    @Override
    public void onAuthenticationSuccess(HttpServletRequest request,
                                        HttpServletResponse response,
                                        Authentication authentication) throws IOException {

        OAuth2User oauth2User = (OAuth2User) authentication.getPrincipal();

        // Atributos que Google devuelve (scope: email + profile)
        String googleId   = oauth2User.getAttribute("sub");
        String email      = oauth2User.getAttribute("email");
        String nombre     = oauth2User.getAttribute("given_name");
        String apellido   = oauth2User.getAttribute("family_name");
        String fotoPerfil = oauth2User.getAttribute("picture");

        log.info("Login OAuth2 exitoso — email: {}, googleId: {}", email, googleId);

        // Crear o actualizar usuario en la BD
        Usuario usuario = usuarioService.findOrCreate(googleId, email, nombre, apellido, fotoPerfil);

        // Generar JWT
        String token = jwtTokenProvider.generateToken(
                usuario.getId(),
                usuario.getEmail(),
                usuario.getNombreCompleto()
        );

        // Redirigir al frontend con el token
        String redirectUrl = UriComponentsBuilder
                .fromUriString(frontendRedirectUri)
                .queryParam("token", token)
                .build()
                .toUriString();

        log.debug("Redirigiendo a frontend: {}", frontendRedirectUri);
        getRedirectStrategy().sendRedirect(request, response, redirectUrl);
    }
}
