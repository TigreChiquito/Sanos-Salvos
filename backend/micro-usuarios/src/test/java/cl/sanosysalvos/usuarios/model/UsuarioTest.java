package cl.sanosysalvos.usuarios.model;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class UsuarioTest {

    @Test
    void getInitials_conNombreYApellido_retornaIniciales() {
        Usuario u = Usuario.builder()
                .nombre("Carlos")
                .apellido("Mendez")
                .email("carlos@test.com")
                .googleId("g-001")
                .build();

        assertThat(u.getInitials()).isEqualTo("CM");
    }

    @Test
    void getInitials_soloNombre_retornaUnaInicial() {
        Usuario u = Usuario.builder()
                .nombre("Ana")
                .apellido("")
                .email("ana@test.com")
                .googleId("g-002")
                .build();

        assertThat(u.getInitials()).isEqualTo("A");
    }

    @Test
    void getInitials_ambosVacios_retornaVacio() {
        Usuario u = Usuario.builder()
                .nombre("")
                .apellido("")
                .email("sin@test.com")
                .googleId("g-003")
                .build();

        assertThat(u.getInitials()).isEmpty();
    }

    @Test
    void getNombreCompleto_concatenaNombreYApellido() {
        Usuario u = Usuario.builder()
                .nombre("Maria")
                .apellido("Lopez")
                .email("m@test.com")
                .googleId("g-004")
                .build();

        assertThat(u.getNombreCompleto()).isEqualTo("Maria Lopez");
    }

    @Test
    void builder_valoresDefaultActivo() {
        Usuario u = Usuario.builder()
                .nombre("Test")
                .apellido("User")
                .email("t@t.com")
                .googleId("g-005")
                .build();

        assertThat(u.getActivo()).isTrue();
        assertThat(u.getNotifEmail()).isTrue();
        assertThat(u.getNotifSistema()).isTrue();
    }
}
