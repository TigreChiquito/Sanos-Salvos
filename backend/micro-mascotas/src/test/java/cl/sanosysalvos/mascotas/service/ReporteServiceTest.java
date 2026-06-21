package cl.sanosysalvos.mascotas.service;

import cl.sanosysalvos.mascotas.dto.ReporteRequestDto;
import cl.sanosysalvos.mascotas.dto.ReporteResponseDto;
import cl.sanosysalvos.mascotas.kafka.ReporteEventProducer;
import cl.sanosysalvos.mascotas.model.mongo.ReporteDocument;
import cl.sanosysalvos.mascotas.model.pg.Reporte;
import cl.sanosysalvos.mascotas.repository.mongo.ReporteMongoRepository;
import cl.sanosysalvos.mascotas.repository.pg.FotoRepository;
import cl.sanosysalvos.mascotas.repository.pg.ReporteRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ReporteServiceTest {

    @Mock private ReporteRepository reporteRepository;
    @Mock private FotoRepository fotoRepository;
    @Mock private ReporteMongoRepository mongoRepository;
    @Mock private MinioService minioService;
    @Mock private ReporteEventProducer eventProducer;

    @InjectMocks
    private ReporteService reporteService;

    // ── crear ──────────────────────────────────────────────────

    @Test
    void crear_sinFotos_persisteReporteYPublicaEvento() {
        UUID usuarioId = UUID.randomUUID();
        ReporteRequestDto dto = buildDto("perdido", "perro");

        Reporte savedReporte = buildReporte(UUID.randomUUID(), usuarioId, "perdido", "perro", "activo");
        when(reporteRepository.save(any())).thenReturn(savedReporte);

        ReporteResponseDto result = reporteService.crear(dto, Collections.emptyList(), usuarioId);

        assertThat(result).isNotNull();
        assertThat(result.getTipo()).isEqualTo("perdido");
        verify(reporteRepository).save(any());
        verify(eventProducer).publishCreated(eq(savedReporte), anyList());
        verify(minioService, never()).subirFoto(any(), any(), anyInt());
    }

    @Test
    void crear_conListaNull_noSubeFotos() {
        UUID usuarioId = UUID.randomUUID();
        ReporteRequestDto dto = buildDto("encontrado", "gato");

        Reporte savedReporte = buildReporte(UUID.randomUUID(), usuarioId, "encontrado", "gato", "activo");
        when(reporteRepository.save(any())).thenReturn(savedReporte);

        ReporteResponseDto result = reporteService.crear(dto, null, usuarioId);

        assertThat(result).isNotNull();
        verify(minioService, never()).subirFoto(any(), any(), anyInt());
    }

    // ── listar ─────────────────────────────────────────────────

    @Test
    void listar_sinFiltros_consultaTodasActivas() {
        when(mongoRepository.findByEstado("activo")).thenReturn(Collections.emptyList());

        List<ReporteResponseDto> result = reporteService.listar(null, null, null, null, null, null);

        assertThat(result).isEmpty();
        verify(mongoRepository).findByEstado("activo");
    }

    @Test
    void listar_conTipo_filtraPorTipo() {
        when(mongoRepository.findByEstadoAndTipo("activo", "perdido")).thenReturn(Collections.emptyList());

        List<ReporteResponseDto> result = reporteService.listar("perdido", null, null, null, null, null);

        verify(mongoRepository).findByEstadoAndTipo("activo", "perdido");
    }

    @Test
    void listar_conAnimal_filtraPorAnimal() {
        when(mongoRepository.findByEstadoAndAnimal("activo", "perro")).thenReturn(Collections.emptyList());

        List<ReporteResponseDto> result = reporteService.listar(null, "perro", null, null, null, null);

        verify(mongoRepository).findByEstadoAndAnimal("activo", "perro");
    }

    @Test
    void listar_conTipoYAnimal_filtraPorAmbos() {
        when(mongoRepository.findByEstadoAndTipoAndAnimal("activo", "perdido", "gato"))
                .thenReturn(Collections.emptyList());

        List<ReporteResponseDto> result = reporteService.listar("perdido", "gato", null, null, null, null);

        verify(mongoRepository).findByEstadoAndTipoAndAnimal("activo", "perdido", "gato");
    }

    @Test
    void listar_conBounds_filtraPorCoordenadasMapa() {
        when(mongoRepository.findActivosEnBounds(-34.0, -33.0, -71.0, -70.0))
                .thenReturn(Collections.emptyList());

        List<ReporteResponseDto> result =
                reporteService.listar(null, null, -34.0, -33.0, -71.0, -70.0);

        verify(mongoRepository).findActivosEnBounds(-34.0, -33.0, -71.0, -70.0);
    }

    @Test
    void listar_retornaDocumentosConvertidos() {
        ReporteDocument doc = buildReporteDocument(UUID.randomUUID().toString(), "perdido", "perro");
        when(mongoRepository.findByEstado("activo")).thenReturn(List.of(doc));

        List<ReporteResponseDto> result = reporteService.listar(null, null, null, null, null, null);

        assertThat(result).hasSize(1);
        assertThat(result.get(0).getTipo()).isEqualTo("perdido");
    }

    // ── obtener ────────────────────────────────────────────────

    @Test
    void obtener_idExistente_retornaDto() {
        UUID id = UUID.randomUUID();
        ReporteDocument doc = buildReporteDocument(id.toString(), "encontrado", "gato");
        when(mongoRepository.findById(id.toString())).thenReturn(Optional.of(doc));

        Optional<ReporteResponseDto> result = reporteService.obtener(id);

        assertThat(result).isPresent();
        assertThat(result.get().getAnimal()).isEqualTo("gato");
    }

    @Test
    void obtener_idInexistente_retornaEmpty() {
        UUID id = UUID.randomUUID();
        when(mongoRepository.findById(id.toString())).thenReturn(Optional.empty());

        Optional<ReporteResponseDto> result = reporteService.obtener(id);

        assertThat(result).isEmpty();
    }

    // ── actualizarEstado ───────────────────────────────────────

    @Test
    void actualizarEstado_propietarioMarcaResuelto_publicaEventoResuelto() {
        UUID id = UUID.randomUUID();
        UUID usuarioId = UUID.randomUUID();
        Reporte reporte = buildReporte(id, usuarioId, "perdido", "perro", "activo");

        when(reporteRepository.findById(id)).thenReturn(Optional.of(reporte));
        when(reporteRepository.save(reporte)).thenReturn(reporte);

        Optional<ReporteResponseDto> result = reporteService.actualizarEstado(id, "resuelto", usuarioId);

        assertThat(result).isPresent();
        assertThat(reporte.getEstado()).isEqualTo("resuelto");
        verify(eventProducer).publishResolved(reporte);
    }

    @Test
    void actualizarEstado_propietarioOtroEstado_publicaEventoUpdated() {
        UUID id = UUID.randomUUID();
        UUID usuarioId = UUID.randomUUID();
        Reporte reporte = buildReporte(id, usuarioId, "perdido", "perro", "activo");

        when(reporteRepository.findById(id)).thenReturn(Optional.of(reporte));
        when(reporteRepository.save(reporte)).thenReturn(reporte);

        reporteService.actualizarEstado(id, "activo", usuarioId);

        verify(eventProducer).publishUpdated(reporte);
    }

    @Test
    void actualizarEstado_otroUsuario_lanzaSecurityException() {
        UUID id = UUID.randomUUID();
        UUID propietario = UUID.randomUUID();
        UUID otroUsuario = UUID.randomUUID();
        Reporte reporte = buildReporte(id, propietario, "perdido", "perro", "activo");

        when(reporteRepository.findById(id)).thenReturn(Optional.of(reporte));

        assertThatThrownBy(() -> reporteService.actualizarEstado(id, "resuelto", otroUsuario))
                .isInstanceOf(SecurityException.class);
    }

    @Test
    void actualizarEstado_idInexistente_retornaEmpty() {
        UUID id = UUID.randomUUID();
        when(reporteRepository.findById(id)).thenReturn(Optional.empty());

        Optional<ReporteResponseDto> result = reporteService.actualizarEstado(id, "resuelto", UUID.randomUUID());

        assertThat(result).isEmpty();
    }

    // ── eliminar ───────────────────────────────────────────────

    @Test
    void eliminar_propietario_marcaComoEliminado() {
        UUID id = UUID.randomUUID();
        UUID usuarioId = UUID.randomUUID();
        Reporte reporte = buildReporte(id, usuarioId, "perdido", "perro", "activo");

        when(reporteRepository.findById(id)).thenReturn(Optional.of(reporte));
        when(reporteRepository.save(reporte)).thenReturn(reporte);

        boolean result = reporteService.eliminar(id, usuarioId);

        assertThat(result).isTrue();
        assertThat(reporte.getEstado()).isEqualTo("eliminado");
        verify(eventProducer).publishDeleted(reporte);
    }

    @Test
    void eliminar_otroUsuario_lanzaSecurityException() {
        UUID id = UUID.randomUUID();
        UUID propietario = UUID.randomUUID();
        Reporte reporte = buildReporte(id, propietario, "perdido", "perro", "activo");

        when(reporteRepository.findById(id)).thenReturn(Optional.of(reporte));

        assertThatThrownBy(() -> reporteService.eliminar(id, UUID.randomUUID()))
                .isInstanceOf(SecurityException.class);
    }

    @Test
    void eliminar_idInexistente_retornaFalse() {
        UUID id = UUID.randomUUID();
        when(reporteRepository.findById(id)).thenReturn(Optional.empty());

        boolean result = reporteService.eliminar(id, UUID.randomUUID());

        assertThat(result).isFalse();
    }

    // ── helpers ────────────────────────────────────────────────

    private ReporteRequestDto buildDto(String tipo, String animal) {
        ReporteRequestDto dto = new ReporteRequestDto();
        dto.setTipo(tipo);
        dto.setAnimal(animal);
        dto.setNombre("Firulais");
        dto.setRaza("Labrador");
        dto.setColor("café");
        dto.setTamano("mediano");
        dto.setDescripcion("Muy amigable");
        dto.setLat(new BigDecimal("-33.4489"));
        dto.setLng(new BigDecimal("-70.6693"));
        dto.setZona("Providencia");
        return dto;
    }

    private Reporte buildReporte(UUID id, UUID usuarioId, String tipo, String animal, String estado) {
        return Reporte.builder()
                .id(id)
                .tipo(tipo)
                .animal(animal)
                .estado(estado)
                .nombre("Firulais")
                .raza("Labrador")
                .color("café")
                .tamano("mediano")
                .lat(new BigDecimal("-33.4489"))
                .lng(new BigDecimal("-70.6693"))
                .zona("Providencia")
                .usuarioId(usuarioId)
                .build();
    }

    private ReporteDocument buildReporteDocument(String id, String tipo, String animal) {
        return ReporteDocument.builder()
                .id(id)
                .tipo(tipo)
                .animal(animal)
                .estado("activo")
                .usuarioId(UUID.randomUUID().toString())
                .fotos(Collections.emptyList())
                .build();
    }
}
