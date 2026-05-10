"""
Consumidor Kafka — escucha eventos de reportes y dispara el matching.

Tópicos escuchados:
  ss.reportes.created  → matching completo para el nuevo reporte
  ss.reportes.updated  → re-matching si cambiaron atributos del reporte

El consumidor corre en un loop async paralelo al servidor FastAPI,
iniciado y detenido con el lifespan de la aplicación.
"""
import asyncio
import json
import logging
from concurrent.futures import ThreadPoolExecutor

from aiokafka import AIOKafkaConsumer

from app.config import settings
from app.database import SessionLocal
from app.kafka.producer import publicar_coincidencia
from app.services import matching_service

log = logging.getLogger(__name__)

# Thread pool para ejecutar el matching (CPU-bound) sin bloquear el event loop
_executor = ThreadPoolExecutor(max_workers=2)


async def start_consumer():
    """Loop principal del consumidor. Llamado desde el lifespan de FastAPI."""
    consumer = AIOKafkaConsumer(
        settings.kafka_topic_reportes_created,
        settings.kafka_topic_reportes_updated,
        bootstrap_servers=settings.kafka_bootstrap_servers,
        group_id=settings.kafka_group_id,
        value_deserializer=lambda v: json.loads(v.decode("utf-8")),
        auto_offset_reset="earliest",
        enable_auto_commit=True,
    )

    await consumer.start()
    log.info("Kafka consumer iniciado — escuchando: %s, %s",
             settings.kafka_topic_reportes_created,
             settings.kafka_topic_reportes_updated)

    try:
        async for msg in consumer:
            await _procesar_mensaje(msg.value)
    except asyncio.CancelledError:
        log.info("Consumer cancelado — cerrando")
    finally:
        await consumer.stop()


async def _procesar_mensaje(payload: dict):
    """
    Despacha el evento al matching_service en un thread separado
    para no bloquear el event loop de FastAPI durante la inferencia ML.
    """
    event_type = payload.get("eventType")
    reporte_id = payload.get("reporteId")

    if not reporte_id:
        log.warning("Evento sin reporteId, ignorando: %s", payload)
        return

    log.info("Evento recibido: %s para reporte %s", event_type, reporte_id)

    loop = asyncio.get_running_loop()

    def _run_matching():
        db = SessionLocal()
        try:
            return matching_service.procesar_reporte(
                db=db,
                reporte_id=reporte_id,
                tipo=payload.get("tipo", ""),
                animal=payload.get("animal", ""),
                nombre=payload.get("nombre") or None,
                raza=payload.get("raza") or None,
                color=payload.get("color") or None,
                tamano=payload.get("tamano") or None,
                descripcion=payload.get("descripcion") or None,
                lat=float(payload.get("lat", 0)),
                lng=float(payload.get("lng", 0)),
                urls_fotos=payload.get("urlsFotos", []),
            )
        except Exception as e:
            log.error("Error en matching para reporte %s: %s", reporte_id, e, exc_info=True)
            return []
        finally:
            db.close()

    coincidencias = await loop.run_in_executor(_executor, _run_matching)

    # Publicar cada coincidencia en Kafka
    for coincidencia in coincidencias:
        await publicar_coincidencia(coincidencia)
