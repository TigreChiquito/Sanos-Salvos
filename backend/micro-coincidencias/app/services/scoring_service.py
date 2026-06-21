"""
Servicio de cálculo de scores de coincidencia entre dos reportes.

Cada dimensión produce un score entre 0.0 y 1.0.
Las dimensiones sin datos (campo None/vacío en ambos reportes) se excluyen
del cálculo y su peso se redistribuye proporcionalmente entre las disponibles.

Dimensiones y pesos base:
  nombre      → 10%  (fuzzy match, solo si ambos tienen nombre)
  raza        → 20%  (fuzzy match, importante para identificar la especie)
  color       → 15%  (fuzzy partial match)
  tamano      → 10%  (match exacto binario)
  descripcion → 20%  (similitud de embeddings de texto)
  ubicacion   → 15%  (distancia geográfica inversa)
  imagen      → 10%  (similitud coseno de embeddings CLIP)
"""
import logging
from typing import Optional

import numpy as np
from rapidfuzz import fuzz
from sklearn.metrics.pairwise import cosine_similarity

from app.config import settings
from app.utils.geo import score_ubicacion as geo_score

log = logging.getLogger(__name__)

# Pesos base (deben sumar 1.0)
PESOS = {
    "nombre":      0.10,
    "raza":        0.20,
    "color":       0.15,
    "tamano":      0.10,
    "descripcion": 0.20,
    "ubicacion":   0.15,
    "imagen":      0.10,
}


def calcular_scores(
    # Reporte A (ej: perdido)
    nombre_a: Optional[str],
    raza_a: Optional[str],
    color_a: Optional[str],
    tamano_a: Optional[str],
    descripcion_a: Optional[str],
    lat_a: float,
    lng_a: float,
    embedding_a: Optional[np.ndarray],
    # Reporte B (ej: encontrado)
    nombre_b: Optional[str],
    raza_b: Optional[str],
    color_b: Optional[str],
    tamano_b: Optional[str],
    descripcion_b: Optional[str],
    lat_b: float,
    lng_b: float,
    embedding_b: Optional[np.ndarray],
) -> dict:
    """
    Calcula los scores individuales por dimensión entre dos reportes.
    Retorna un dict con claves de PESOS + 'total'.
    """
    scores: dict[str, Optional[float]] = {}

    # ── Nombre ────────────────────────────────────────────────
    if nombre_a and nombre_b:
        scores["nombre"] = round(
            fuzz.token_sort_ratio(nombre_a.lower(), nombre_b.lower()) / 100.0, 4
        )
    else:
        scores["nombre"] = None  # No penalizar si falta el nombre

    # ── Raza ──────────────────────────────────────────────────
    if raza_a and raza_b:
        scores["raza"] = round(
            fuzz.token_sort_ratio(raza_a.lower(), raza_b.lower()) / 100.0, 4
        )
    else:
        scores["raza"] = None

    # ── Color ─────────────────────────────────────────────────
    if color_a and color_b:
        scores["color"] = round(
            fuzz.partial_ratio(color_a.lower(), color_b.lower()) / 100.0, 4
        )
    else:
        scores["color"] = None

    # ── Tamaño (match binario) ────────────────────────────────
    if tamano_a and tamano_b:
        scores["tamano"] = 1.0 if tamano_a == tamano_b else 0.0
    else:
        scores["tamano"] = None

    # ── Descripción (coseno entre embeddings de texto) ────────
    # Los embeddings ya incluyen la descripción; se comparan directamente
    if embedding_a is not None and embedding_b is not None:
        sim = cosine_similarity(
            embedding_a.reshape(1, -1),
            embedding_b.reshape(1, -1)
        )[0][0]
        scores["descripcion"] = round(float(sim), 4)
    else:
        scores["descripcion"] = None

    # ── Ubicación ─────────────────────────────────────────────
    scores["ubicacion"] = geo_score(lat_a, lng_a, lat_b, lng_b, settings.max_distancia_km)

    # ── Imagen (coseno del embedding combinado texto+imagen) ──
    # Reutiliza el mismo embedding combinado; ya incorpora la imagen
    if embedding_a is not None and embedding_b is not None:
        sim = cosine_similarity(
            embedding_a.reshape(1, -1),
            embedding_b.reshape(1, -1)
        )[0][0]
        scores["imagen"] = round(float(sim), 4)
    else:
        scores["imagen"] = None

    # ── Score total ponderado ─────────────────────────────────
    scores["total"] = _calcular_total(scores)
    return scores


def _calcular_total(scores: dict) -> float:
    """
    Promedio ponderado de los scores disponibles.
    Los campos None redistribuyen su peso entre los disponibles.
    """
    activos = {k: v for k, v in scores.items()
               if k in PESOS and v is not None}

    if not activos:
        return 0.0

    peso_total = sum(PESOS[k] for k in activos)
    total = sum(scores[k] * (PESOS[k] / peso_total) for k in activos)
    return round(float(total), 4)
