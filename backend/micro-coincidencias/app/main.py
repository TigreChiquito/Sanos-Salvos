"""
Sanos & Salvos — Motor de Coincidencias
FastAPI + Uvicorn/Gunicorn
"""
import asyncio
import logging
import os
from contextlib import asynccontextmanager

from fastapi import FastAPI
from fastapi.middleware.cors import CORSMiddleware
from prometheus_client import CollectorRegistry, multiprocess, make_asgi_app, CONTENT_TYPE_LATEST, generate_latest, PlatformCollector, ProcessCollector
from prometheus_fastapi_instrumentator import Instrumentator
from starlette.requests import Request
from starlette.responses import Response

from app.config import settings
from app.kafka.consumer import start_consumer
from app.kafka.producer import start_producer, stop_producer
from app.routers import coincidencias

logging.basicConfig(
    level=logging.DEBUG if settings.debug else logging.INFO,
    format="%(asctime)s [%(levelname)s] %(name)s - %(message)s",
)
log = logging.getLogger(__name__)


@asynccontextmanager
async def lifespan(app: FastAPI):
    """
    Gestiona el ciclo de vida de la aplicacion.
    Al iniciar: arranca el producer Kafka y lanza el consumer en background.
    Al cerrar: cancela el consumer y detiene el producer.
    """
    log.info("Iniciando %s...", settings.app_name)

    # Iniciar producer
    await start_producer()

    # Lanzar consumer en background (corre en paralelo al servidor)
    consumer_task = asyncio.create_task(start_consumer())

    log.info("Motor de Coincidencias listo")
    yield

    # Shutdown
    log.info("Apagando Motor de Coincidencias...")
    consumer_task.cancel()
    try:
        await consumer_task
    except asyncio.CancelledError:
        pass
    await stop_producer()


app = FastAPI(
    title="Sanos & Salvos - Motor de Coincidencias",
    description="Matching multidimensional de mascotas perdidas y encontradas",
    version="0.1.0",
    lifespan=lifespan,
)

# Métricas Prometheus
# Si hay PROMETHEUS_MULTIPROC_DIR configurado (modo multiprocess con gunicorn),
# usamos un registry que agrega métricas de todos los workers.
# Con 1 solo worker (desarrollo) funciona igual que el registry por defecto.
_multiproc_dir = os.environ.get("PROMETHEUS_MULTIPROC_DIR")

if _multiproc_dir:
    # Modo multiprocess: registry que lee de los archivos del directorio compartido
    def _metrics_endpoint(request: Request) -> Response:
        registry = CollectorRegistry()
        multiprocess.MultiProcessCollector(registry)
        # Agrega métricas de proceso (CPU, memoria, FDs) — no vienen del directorio multiproc
        ProcessCollector(registry=registry)
        PlatformCollector(registry=registry)
        data = generate_latest(registry)
        return Response(content=data, media_type=CONTENT_TYPE_LATEST)

    app.add_route("/metrics", _metrics_endpoint, methods=["GET"])
    Instrumentator(
        should_group_status_codes=False,
        excluded_handlers=["/health", "/metrics"],
    ).instrument(app)
else:
    Instrumentator(
        should_group_status_codes=False,
        excluded_handlers=["/health", "/metrics"],
    ).instrument(app).expose(app)

# CORS
app.add_middleware(
    CORSMiddleware,
    allow_origins=["*"],  # El orquestador filtra antes de llegar aqui
    allow_methods=["*"],
    allow_headers=["*"],
)

# Routers
app.include_router(coincidencias.router)


@app.get("/health")
def health():
    return {"status": "ok", "service": settings.app_name}
