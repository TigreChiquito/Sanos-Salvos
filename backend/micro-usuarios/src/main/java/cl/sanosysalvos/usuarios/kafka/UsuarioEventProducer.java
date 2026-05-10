package cl.sanosysalvos.usuarios.kafka;

import cl.sanosysalvos.usuarios.model.Usuario;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

import java.time.OffsetDateTime;
import java.util.Map;

/**
 * Publica eventos de usuarios en Kafka.
 *
 * Tópicos:
 *   ss.usuarios.created  → cuando un usuario se registra por primera vez
 *   ss.usuarios.updated  → cuando se actualiza el perfil
 *
 * Nota: Debezium también capturará estos cambios desde PostgreSQL,
 * pero los eventos explícitos permiten incluir contexto de negocio
 * que el CDC no puede inferir (ej: si fue primer login vs actualización).
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class UsuarioEventProducer {

    private static final String TOPIC_CREATED = "ss.usuarios.created";
    private static final String TOPIC_UPDATED = "ss.usuarios.updated";

    private final KafkaTemplate<String, Object> kafkaTemplate;

    /** Publica evento de usuario creado */
    public void publishCreated(Usuario usuario) {
        publish(TOPIC_CREATED, usuario, "CREATED");
    }

    /** Publica evento de usuario actualizado */
    public void publishUpdated(Usuario usuario) {
        publish(TOPIC_UPDATED, usuario, "UPDATED");
    }

    // ── Privado ────────────────────────────────────────────────

    private void publish(String topic, Usuario usuario, String eventType) {
        Map<String, Object> payload = Map.of(
                "eventType",  eventType,
                "userId",     usuario.getId().toString(),
                "email",      usuario.getEmail(),
                "nombre",     usuario.getNombre(),
                "apellido",   usuario.getApellido(),
                "timestamp",  OffsetDateTime.now().toString()
        );

        kafkaTemplate.send(topic, usuario.getId().toString(), payload)
                .whenComplete((result, ex) -> {
                    if (ex != null) {
                        log.error("Error publicando evento {} para usuario {}: {}",
                                eventType, usuario.getId(), ex.getMessage());
                    } else {
                        log.debug("Evento {} publicado — topic: {}, offset: {}",
                                eventType, topic,
                                result.getRecordMetadata().offset());
                    }
                });
    }
}
