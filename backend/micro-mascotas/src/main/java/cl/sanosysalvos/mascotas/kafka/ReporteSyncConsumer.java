package cl.sanosysalvos.mascotas.kafka;

import cl.sanosysalvos.mascotas.model.mongo.ReporteDocument;
import cl.sanosysalvos.mascotas.repository.mongo.ReporteMongoRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.mongodb.core.geo.GeoJsonPoint;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.List;
import java.util.Map;

/**
 * Consumidor Kafka responsable de mantener MongoDB sincronizado con PostgreSQL.
 *
 * Escucha el tópico ss.reportes.sync, que el ReporteEventProducer publica
 * en cada operación de escritura (CREATE, UPDATE, RESOLVED, DELETE).
 *
 * Este es el mecanismo CQRS:
 *   PostgreSQL (escritura) → Kafka → MongoDB (lectura)
 *
 * Kafka garantiza que los mensajes se procesan en orden por partición
 * (la key es el reporteId, así que el mismo reporte siempre va a la
 * misma partición y se procesa secuencialmente).
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ReporteSyncConsumer {

    private final ReporteMongoRepository mongoRepository;

    @KafkaListener(
            topics = "ss.reportes.sync",
            groupId = "ss-mascotas-sync",
            containerFactory = "kafkaListenerContainerFactory"
    )
    public void onReporteSync(Map<String, Object> payload) {
        try {
            String eventType = (String) payload.get("eventType");
            String reporteId = (String) payload.get("reporteId");

            log.debug("Sync event recibido: {} para reporte {}", eventType, reporteId);

            switch (eventType) {
                case "CREATED", "UPDATED", "RESOLVED" -> upsertInMongo(reporteId, payload);
                case "DELETED" -> mongoRepository.deleteById(reporteId);
                default -> log.warn("Tipo de evento desconocido: {}", eventType);
            }

        } catch (Exception e) {
            log.error("Error procesando evento de sync: {}", e.getMessage(), e);
            // En producción: implementar DLQ (Dead Letter Queue) para reintentos
        }
    }

    // ── Privado ────────────────────────────────────────────────

    @SuppressWarnings("unchecked")
    private void upsertInMongo(String reporteId, Map<String, Object> payload) {
        Double lat = toDouble(payload.get("lat"));
        Double lng = toDouble(payload.get("lng"));

        // Fotos: el payload incluye las URLs públicas desnormalizadas
        List<String> urlsFotos = (List<String>) payload.getOrDefault("urlsFotos", List.of());
        List<ReporteDocument.FotoEmbedded> fotosEmbedded = urlsFotos.stream()
                .map(url -> ReporteDocument.FotoEmbedded.builder()
                        .id(java.util.UUID.randomUUID().toString())
                        .url(url)
                        .orden(urlsFotos.indexOf(url))
                        .build())
                .toList();

        ReporteDocument doc = ReporteDocument.builder()
                .id(reporteId)
                .tipo((String) payload.get("tipo"))
                .animal((String) payload.get("animal"))
                .estado((String) payload.get("estado"))
                .nombre((String) payload.get("nombre"))
                .raza((String) payload.get("raza"))
                .color((String) payload.get("color"))
                .tamano((String) payload.get("tamano"))
                .descripcion((String) payload.get("descripcion"))
                .lat(lat)
                .lng(lng)
                .ubicacion(lat != null && lng != null ? new GeoJsonPoint(lng, lat) : null)
                .zona((String) payload.get("zona"))
                .usuarioId((String) payload.get("usuarioId"))
                .fotos(fotosEmbedded)
                .updatedAt(Instant.now())
                .build();

        // Si ya existe, preservar el createdAt original
        mongoRepository.findById(reporteId).ifPresentOrElse(
                existing -> {
                    doc.setCreatedAt(existing.getCreatedAt());
                    mongoRepository.save(doc);
                },
                () -> {
                    doc.setCreatedAt(Instant.now());
                    mongoRepository.save(doc);
                }
        );

        log.debug("Reporte {} sincronizado en MongoDB", reporteId);
    }

    private Double toDouble(Object val) {
        if (val == null) return null;
        if (val instanceof Double d) return d;
        if (val instanceof Number n) return n.doubleValue();
        return null;
    }
}
