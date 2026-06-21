package cl.sanosysalvos.orquestador.security;

import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import javax.crypto.SecretKey;
import java.util.Base64;
import java.util.Date;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class JwtTokenProviderTest {

    private static final String SECRET = "dev-secret-change-in-production-min-32-chars";
    private JwtTokenProvider provider;
    private SecretKey key;

    @BeforeEach
    void setUp() {
        provider = new JwtTokenProvider(SECRET);
        byte[] keyBytes = Base64.getEncoder().encode(SECRET.getBytes());
        key = Keys.hmacShaKeyFor(
                io.jsonwebtoken.io.Decoders.BASE64.decode(
                        Base64.getEncoder().encodeToString(SECRET.getBytes())
                )
        );
    }

    private String buildToken(UUID userId, String email, String name, long expirationMs) {
        return Jwts.builder()
                .subject(userId.toString())
                .claim("email", email)
                .claim("name", name)
                .issuedAt(new Date())
                .expiration(new Date(System.currentTimeMillis() + expirationMs))
                .signWith(key)
                .compact();
    }

    @Test
    void validateToken_tokenValido_retornaTrue() {
        String token = buildToken(UUID.randomUUID(), "test@mail.com", "Test", 60_000);
        assertThat(provider.validateToken(token)).isTrue();
    }

    @Test
    void validateToken_tokenExpirado_retornaFalse() {
        String token = buildToken(UUID.randomUUID(), "test@mail.com", "Test", -1000);
        assertThat(provider.validateToken(token)).isFalse();
    }

    @Test
    void validateToken_tokenInvalido_retornaFalse() {
        assertThat(provider.validateToken("esto.no.es.un.jwt")).isFalse();
    }

    @Test
    void validateToken_tokenVacio_retornaFalse() {
        assertThat(provider.validateToken("")).isFalse();
    }

    @Test
    void getUserId_tokenValido_retornaUUID() {
        UUID userId = UUID.randomUUID();
        String token = buildToken(userId, "test@mail.com", "Test", 60_000);
        assertThat(provider.getUserId(token)).isEqualTo(userId);
    }

    @Test
    void getEmail_tokenValido_retornaEmail() {
        String token = buildToken(UUID.randomUUID(), "joaquin@mail.com", "Joaquín", 60_000);
        assertThat(provider.getEmail(token)).isEqualTo("joaquin@mail.com");
    }

    @Test
    void getName_tokenValido_retornaName() {
        String token = buildToken(UUID.randomUUID(), "test@mail.com", "Joaquín Moya", 60_000);
        assertThat(provider.getName(token)).isEqualTo("Joaquín Moya");
    }

    @Test
    void validateToken_firmaDiferente_retornaFalse() {
        SecretKey otherKey = Keys.hmacShaKeyFor(
                io.jsonwebtoken.io.Decoders.BASE64.decode(
                        Base64.getEncoder().encodeToString("otra-clave-completamente-diferente-32c".getBytes())
                )
        );
        String tokenFirmadoConOtroKey = Jwts.builder()
                .subject(UUID.randomUUID().toString())
                .expiration(new Date(System.currentTimeMillis() + 60_000))
                .signWith(otherKey)
                .compact();
        assertThat(provider.validateToken(tokenFirmadoConOtroKey)).isFalse();
    }
}
