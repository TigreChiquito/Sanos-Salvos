package cl.sanosysalvos.mascotas.kafka;

import cl.sanosysalvos.mascotas.model.pg.Reporte;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;

/**
 * Publica eventos de reportes en Kafka.
 *
 * Tópicos:
 *   ss.reportes.created  → Micro 3 (Motor de Coincidencias) escucha este tópico
 *                          para calcular matches con otros reportes activos.
 *   ss.reportes.updated  → Micro 3 recalcula matches si cambian atributos relevantes.
 *   ss.reportes.resolved → Notificación de que una mascota fue encontrada/resuelta.
 *   ss.reportes.sync     → Consumido por ReporteSyncConsumer para sync a MongoDB.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ReporteEventProducer {

    private static final String TOPIC_CREATED  = "ss.reportes.created";
    private static final String TOPIC_UPDATED  = "ss.reportes.updated";
    private static final String TOPIC_RESOLVED = "ss.reportes.resolved";
    private static final String TOPIC_SYNC     = "ss.reportes.sync";

    private final KafkaTemplate<String, Object> kafkaTemplate;

    public void publishCreated(Reporte reporte, List<String> urlsFotos) {
        publish(TOPIC_CREATED, reporte, "CREATED", urlsFotos);
        publish(TOPIC_SYNC,    reporte, "CREATED", urlsFotos);
    }

    public void publishUpdated(Reporte reporte) {
        publish(TOPIC_UPDATED, reporte, "UPDATED", List.of());
        publish(TOPIC_SYNC,    reporte, "UPDATED", List.of());
    }

    public void publishResolved(Reporte reporte) {
        publish(TOPIC_RESOLVED, reporte, "RESOLVED", List.of());
        publish(TOPIC_SYNC,     reporte, "RESOLVED", List.of());
    }

    public void publishDeleted(Reporte reporte) {
        publish(TOPIC_SYNC, reporte, "DELETED", List.of());
    }

    // ── Privado ────────────────────────────────────────────────

    private void publish(String topic, Reporte r, String eventType, List<String> urlsFotos) {
        Map<String, Object> payload = Map.of(
                "eventType",   eventType,
                "reporteId",   r.getId().toString(),
                "tipo",        r.getTipo(),
                "animal",      r.getAnimal(),
                "estado",      r.getEstado(),
                "nombre",      r.getNombre() != null ? r.getNombre() : "",
                "raza",        r.getRaza()   != null ? r.getRaza()   : "",
                "color",       r.getColor()  != null ? r.getColor()  : "",
                "tamano",      r.getTamano() != null ? r.getTamano() : "",
                "descripcion", r.getDescripcion() != null ? r.getDescripcion() : "",
                "lat",         r.getLat().doubleValue(),
                "lng",         r.getLng().doubleValue(),
                "zona",        r.getZona() != null ? r.getZona() : "",
                "usuarioId",   r.getUsuarioId().toString(),
                "urlsFotos",   urlsFotos,
                "timestamp",   OffsetDateTime.now().toString()
        );

        kafkaTemplate.send(topic, r.getId().toString(), payload)
                .whenComplete((result, ex) -> {
                    if (ex != null) {
                        log.error("Error publicando {} en {}: {}", eventType, topic, ex.getMessage());
                    } else {
                        log.debug("Evento {} publicado en topic {}", eventType, topic);
                    }
                });
    }
}
