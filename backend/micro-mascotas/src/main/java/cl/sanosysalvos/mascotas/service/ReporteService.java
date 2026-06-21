package cl.sanosysalvos.mascotas.service;

import cl.sanosysalvos.mascotas.dto.ReporteRequestDto;
import cl.sanosysalvos.mascotas.dto.ReporteResponseDto;
import cl.sanosysalvos.mascotas.kafka.ReporteEventProducer;
import cl.sanosysalvos.mascotas.model.pg.Foto;
import cl.sanosysalvos.mascotas.model.pg.Reporte;
import cl.sanosysalvos.mascotas.repository.mongo.ReporteMongoRepository;
import cl.sanosysalvos.mascotas.repository.pg.FotoRepository;
import cl.sanosysalvos.mascotas.repository.pg.ReporteRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class ReporteService {

    private final ReporteRepository reporteRepository;
    private final FotoRepository fotoRepository;
    private final ReporteMongoRepository mongoRepository;
    private final MinioService minioService;
    private final ReporteEventProducer eventProducer;

    /**
     * Crea un nuevo reporte con sus fotos.
     * Escribe en PostgreSQL y publica evento Kafka para sync a MongoDB.
     */
    @Transactional
    public ReporteResponseDto crear(ReporteRequestDto dto, List<MultipartFile> archivos, UUID usuarioId) {
        // 1. Persistir el reporte en PostgreSQL
        Reporte reporte = Reporte.builder()
                .tipo(dto.getTipo())
                .animal(dto.getAnimal())
                .nombre(dto.getNombre())
                .raza(dto.getRaza())
                .color(dto.getColor())
                .tamano(dto.getTamano())
                .descripcion(dto.getDescripcion())
                .lat(dto.getLat())
                .lng(dto.getLng())
                .zona(dto.getZona())
                .usuarioId(usuarioId)
                .build();

        reporte = reporteRepository.save(reporte);
        log.info("Reporte creado: {} ({})", reporte.getId(), reporte.getTipo());

        // 2. Subir fotos a MinIO y persistir en PostgreSQL
        List<String> urlsFotos = new ArrayList<>();
        if (archivos != null && !archivos.isEmpty()) {
            final Reporte reporteFinal = reporte;
            for (int i = 0; i < Math.min(archivos.size(), 5); i++) {
                MultipartFile archivo = archivos.get(i);
                if (archivo.isEmpty()) continue;

                String[] resultado = minioService.subirFoto(reporteFinal.getId(), archivo, i);
                String bucketKey = resultado[0];
                String url       = resultado[1];

                Foto foto = Foto.builder()
                        .reporte(reporteFinal)
                        .bucketKey(bucketKey)
                        .url(url)
                        .orden(i)
                        .build();

                fotoRepository.save(foto);
                reporteFinal.getFotos().add(foto);
                urlsFotos.add(url);
            }
        }

        // 3. Publicar evento Kafka (dispara sync a MongoDB + Motor de Coincidencias)
        eventProducer.publishCreated(reporte, urlsFotos);

        return ReporteResponseDto.from(reporte);
    }

    /**
     * Busca reportes activos con filtros opcionales.
     * Lee desde MongoDB (capa de lectura CQRS).
     */
    public List<ReporteResponseDto> listar(String tipo, String animal,
                                            Double latMin, Double latMax,
                                            Double lngMin, Double lngMax) {
        List<?> docs;

        boolean tieneBounds = latMin != null && latMax != null && lngMin != null && lngMax != null;

        if (tieneBounds) {
            docs = mongoRepository.findActivosEnBounds(latMin, latMax, lngMin, lngMax);
        } else if (tipo != null && animal != null) {
            docs = mongoRepository.findByEstadoAndTipoAndAnimal("activo", tipo, animal);
        } else if (tipo != null) {
            docs = mongoRepository.findByEstadoAndTipo("activo", tipo);
        } else if (animal != null) {
            docs = mongoRepository.findByEstadoAndAnimal("activo", animal);
        } else {
            docs = mongoRepository.findByEstado("activo");
        }

        return docs.stream()
                .map(d -> ReporteResponseDto.from(
                        (cl.sanosysalvos.mascotas.model.mongo.ReporteDocument) d))
                .toList();
    }

    /**
     * Detalle de un reporte por ID.
     * Lee desde MongoDB para consistencia con el listado.
     */
    public Optional<ReporteResponseDto> obtener(UUID id) {
        return mongoRepository.findById(id.toString())
                .map(ReporteResponseDto::from);
    }

    /**
     * Actualiza estado de un reporte (ej: marcar como resuelto).
     * Solo el autor puede hacerlo.
     */
    @Transactional
    public Optional<ReporteResponseDto> actualizarEstado(UUID id, String nuevoEstado, UUID usuarioId) {
        return reporteRepository.findById(id).map(reporte -> {
            if (!reporte.getUsuarioId().equals(usuarioId)) {
                throw new SecurityException("No tienes permiso para modificar este reporte");
            }
            reporte.setEstado(nuevoEstado);
            Reporte actualizado = reporteRepository.save(reporte);

            if ("resuelto".equals(nuevoEstado)) {
                eventProducer.publishResolved(actualizado);
            } else {
                eventProducer.publishUpdated(actualizado);
            }

            return ReporteResponseDto.from(actualizado);
        });
    }

    /**
     * Elimina un reporte y sus fotos de MinIO.
     * Solo el autor puede hacerlo.
     */
    @Transactional
    public boolean eliminar(UUID id, UUID usuarioId) {
        return reporteRepository.findById(id).map(reporte -> {
            if (!reporte.getUsuarioId().equals(usuarioId)) {
                throw new SecurityException("No tienes permiso para eliminar este reporte");
            }

            // Eliminar fotos de MinIO
            reporte.getFotos().forEach(f -> minioService.eliminarFoto(f.getBucketKey()));

            // Soft delete: marcar como eliminado en lugar de borrar físicamente
            reporte.setEstado("eliminado");
            reporteRepository.save(reporte);
            eventProducer.publishDeleted(reporte);

            log.info("Reporte {} marcado como eliminado por usuario {}", id, usuarioId);
            return true;
        }).orElse(false);
    }
}
