"""
Productor Kafka — publica eventos de coincidencias encontradas.

El orquestador (Springboot) y el micro de usuarios escucharán
ss.coincidencias.found para notificar a los dueños de las mascotas.
"""
import json
import logging
from datetime import datetime, timezone

from aiokafka import AIOKafkaProducer

from app.config import settings
from app.models.coincidencia import Coincidencia

log = logging.getLogger(__name__)

_producer: AIOKafkaProducer | None = None


async def start_producer():
    global _producer
    _producer = AIOKafkaProducer(
        bootstrap_servers=settings.kafka_bootstrap_servers,
        value_serializer=lambda v: json.dumps(v).encode("utf-8"),
        key_serializer=lambda k: k.encode("utf-8") if k else None,
    )
    await _producer.start()
    log.info("Kafka producer iniciado")


async def stop_producer():
    global _producer
    if _producer:
        await _producer.stop()
        log.info("Kafka producer detenido")


async def publicar_coincidencia(coincidencia: Coincidencia):
    """Publica un evento de nueva coincidencia en Kafka."""
    if not _producer:
        log.error("Producer no inicializado")
        return

    payload = {
        "eventType":            "COINCIDENCIA_ENCONTRADA",
        "coincidenciaId":       str(coincidencia.id),
        "reportePerdidoId":     str(coincidencia.reporte_perdido_id),
        "reporteEncontradoId":  str(coincidencia.reporte_encontrado_id),
        "scoreTotal":           float(coincidencia.score_total),
        "estado":               coincidencia.estado,
        "timestamp":            datetime.now(timezone.utc).isoformat(),
    }

    try:
        await _producer.send_and_wait(
            settings.kafka_topic_coincidencias,
            key=str(coincidencia.id),
            value=payload,
        )
        log.info("Coincidencia publicada en Kafka: %s (score: %.4f)",
                 coincidencia.id, float(coincidencia.score_total))
    except Exception as e:
        log.error("Error publicando coincidencia en Kafka: %s", e)
