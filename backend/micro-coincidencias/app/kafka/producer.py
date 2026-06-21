"""
Productor Kafka — publica eventos de coincidencias encontradas.

micro-usuarios escuchará ss.coincidencias.found para notificar a los dueños.
"""
import json
import logging
from datetime import datetime, timezone

from aiokafka import AIOKafkaProducer

from app.config import settings

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


async def publicar_coincidencia(item: dict):
    """
    Publica un evento de coincidencia en Kafka.
    `item` es un dict puro serializado por matching_service (sin ORM objects).
    """
    if not _producer:
        log.error("Producer no inicializado")
        return

    score_total = item["score_total"]
    payload = {
        "eventType":            "COINCIDENCIA_ENCONTRADA",
        "coincidenciaId":       item["coincidencia_id"],
        "reportePerdidoId":     item["reporte_perdido_id"],
        "reporteEncontradoId":  item["reporte_encontrado_id"],
        "usuarioPerdidoId":     item["usuario_perdido_id"],
        "usuarioEncontradoId":  item["usuario_encontrado_id"],
        "scoreTotal":           score_total,
        "scorePorcentaje":      round(score_total * 100, 1),
        "estado":               item["estado"],
        "timestamp":            datetime.now(timezone.utc).isoformat(),
    }

    try:
        await _producer.send_and_wait(
            settings.kafka_topic_coincidencias,
            key=item["coincidencia_id"],
            value=payload,
        )
        log.info("Coincidencia publicada en Kafka: %s (score: %.4f)",
                 item["coincidencia_id"], score_total)
    except Exception as e:
        log.error("Error publicando coincidencia en Kafka: %s", e)
