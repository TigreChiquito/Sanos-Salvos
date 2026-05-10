# =============================================================
#  Sanos & Salvos — Dockerfile micro-coincidencias
#  Python 3.11 slim + PyTorch CPU-only
#
#  Los modelos ML se descargan durante el build para que el
#  contenedor arranque sin necesidad de acceso a internet.
# =============================================================

FROM python:3.11-slim AS base

# Dependencias del sistema para Pillow, psycopg2 y compilación
RUN apt-get update && apt-get install -y --no-install-recommends \
    gcc \
    libpq-dev \
    git \
    && rm -rf /var/lib/apt/lists/*

WORKDIR /app

# ── Instalar PyTorch CPU-only primero (paquete pesado, capa cacheada) ──
RUN pip install --no-cache-dir \
    torch==2.4.1+cpu \
    torchvision==0.19.1+cpu \
    --index-url https://download.pytorch.org/whl/cpu

# ── Instalar resto de dependencias ─────────────────────────────
COPY requirements.txt .
RUN pip install --no-cache-dir -r requirements.txt

# ── Pre-descargar modelos ML en tiempo de build ────────────────
# Esto evita descargas de ~800MB en cada arranque del contenedor
RUN python -c "\
from sentence_transformers import SentenceTransformer; \
SentenceTransformer('paraphrase-multilingual-mpnet-base-v2'); \
print('Modelo de texto descargado')"

RUN python -c "\
import clip; \
clip.load('ViT-B/32', device='cpu'); \
print('Modelo CLIP descargado')"

# ── Copiar código fuente ────────────────────────────────────────
COPY app ./app

# Usuario no-root por seguridad
RUN addgroup --system ssgroup && adduser --system --ingroup ssgroup ssuser
USER ssuser

EXPOSE 8083

# Gunicorn con workers Uvicorn para producción
# En desarrollo puedes reemplazar por: uvicorn app.main:app --reload
CMD ["gunicorn", "app.main:app", \
     "--workers", "2", \
     "--worker-class", "uvicorn.workers.UvicornWorker", \
     "--bind", "0.0.0.0:8083", \
     "--timeout", "120"]
