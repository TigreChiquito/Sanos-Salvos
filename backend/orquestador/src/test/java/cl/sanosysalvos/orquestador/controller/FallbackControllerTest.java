package cl.sanosysalvos.orquestador.controller;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import reactor.test.StepVerifier;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class FallbackControllerTest {

    private FallbackController controller;

    @BeforeEach
    void setUp() {
        controller = new FallbackController();
    }

    @Test
    void fallbackUsuarios_retorna503() {
        StepVerifier.create(controller.fallbackUsuarios())
                .assertNext(response -> {
                    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.SERVICE_UNAVAILABLE);
                    Map<String, Object> body = response.getBody();
                    assertThat(body).containsKey("error");
                    assertThat(body).containsEntry("servicio", "micro-usuarios");
                    assertThat(body).containsKey("mensaje");
                    assertThat(body).containsKey("timestamp");
                })
                .verifyComplete();
    }

    @Test
    void fallbackMascotas_retorna503() {
        StepVerifier.create(controller.fallbackMascotas())
                .assertNext(response -> {
                    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.SERVICE_UNAVAILABLE);
                    Map<String, Object> body = response.getBody();
                    assertThat(body).containsEntry("servicio", "micro-mascotas");
                    assertThat(body).containsEntry("error", "SERVICE_UNAVAILABLE");
                })
                .verifyComplete();
    }

    @Test
    void fallbackCoincidencias_retorna503() {
        StepVerifier.create(controller.fallbackCoincidencias())
                .assertNext(response -> {
                    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.SERVICE_UNAVAILABLE);
                    Map<String, Object> body = response.getBody();
                    assertThat(body).containsEntry("servicio", "micro-coincidencias");
                    assertThat(body).containsEntry("error", "SERVICE_UNAVAILABLE");
                })
                .verifyComplete();
    }

    @Test
    void fallbackUsuarios_mensajeContienePalabraAutenticacion() {
        StepVerifier.create(controller.fallbackUsuarios())
                .assertNext(response -> {
                    String mensaje = (String) response.getBody().get("mensaje");
                    assertThat(mensaje).containsIgnoringCase("autenticación");
                })
                .verifyComplete();
    }

    @Test
    void fallbackMascotas_mensajeContienePalabraReportes() {
        StepVerifier.create(controller.fallbackMascotas())
                .assertNext(response -> {
                    String mensaje = (String) response.getBody().get("mensaje");
                    assertThat(mensaje).containsIgnoringCase("reportes");
                })
                .verifyComplete();
    }

    @Test
    void fallbackCoincidencias_mensajeContienePalabraMotor() {
        StepVerifier.create(controller.fallbackCoincidencias())
                .assertNext(response -> {
                    String mensaje = (String) response.getBody().get("mensaje");
                    assertThat(mensaje).containsIgnoringCase("motor");
                })
                .verifyComplete();
    }

    @Test
    void todosFallbacks_tienenTimestamp() {
        StepVerifier.create(controller.fallbackUsuarios())
                .assertNext(r -> assertThat(r.getBody()).containsKey("timestamp"))
                .verifyComplete();
        StepVerifier.create(controller.fallbackMascotas())
                .assertNext(r -> assertThat(r.getBody()).containsKey("timestamp"))
                .verifyComplete();
        StepVerifier.create(controller.fallbackCoincidencias())
                .assertNext(r -> assertThat(r.getBody()).containsKey("timestamp"))
                .verifyComplete();
    }
}
