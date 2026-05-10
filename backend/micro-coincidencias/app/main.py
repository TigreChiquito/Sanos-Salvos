"""
Sanos & Salvos — Motor de Coincidencias
FastAPI + Uvicorn/Gunicorn
"""
import asyncio
import logging
from contextlib import asynccontextmanager

from fastapi import FastAPI
from fastapi.middleware.cors import CORSMiddleware

from app.config import settings
from app.kafka.consumer import start_consumer
from app.kafka.producer import start_producer, stop_producer
from app.routers import coincidencias

logging.basicConfig(
    level=logging.DEBUG if settings.debug else logging.INFO,
    format="%(asctime)s [%(levelname)s] %(name)s — %(message)s",
)
log = logging.getLogger(__name__)


@asynccontextmanager
async def lifespan(app: FastAPI):
    """
    Gestiona el ciclo de vida de la aplicación.
    Al iniciar: arranca el producer Kafka y lanza el consumer en background.
    Al cerrar: cancela el consumer y detiene el producer.
    """
    log.info("Iniciando %s...", settings.app_name)

    # Iniciar producer
    await start_producer()

    # Lanzar consumer en background (corre en paralelo al servidor)
    consumer_task = asyncio.create_task(start_consumer())

    log.info("Motor de Coincidencias listo 🐾")
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
    title="Sanos & Salvos — Motor de Coincidencias",
    description="Matching multidimensional de mascotas perdidas y encontradas",
    version="0.1.0",
    lifespan=lifespan,
)

# ── CORS ──────────────────────────────────────────────────────
app.add_middleware(
    CORSMiddleware,
    allow_origins=["*"],  # El orquestador filtra antes de llegar aquí
    allow_methods=["*"],
    allow_headers=["*"],
)

# ── Routers ───────────────────────────────────────────────────
app.include_router(coincidencias.router)


@app.get("/health")
def health():
    return {"status": "ok", "service": settings.app_name}
