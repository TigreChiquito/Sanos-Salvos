"""
Orquestador del Motor de Coincidencias.

Flujo por cada nuevo reporte:
  1. Generar embeddings separados (texto 768D + imagen CLIP 512D) → guardar en PostgreSQL
  2. Buscar candidatos del tipo opuesto (perdido ↔ encontrado), misma especie
  3. Calcular scores contra cada candidato usando embeddings independientes
  4. Si score_total >= threshold → crear/actualizar coincidencia en PostgreSQL
  5. Retornar lista de coincidencias encontradas para que el producer las publique
"""
import logging
import uuid
from datetime import datetime, timezone
from typing import Optional

import numpy as np
from sqlalchemy.orm import Session

from app.config import settings
from app.models.coincidencia import Coincidencia
from app.models.reporte import Reporte
from app.services.embedding_service import embedding_service
from app.services.scoring_service import calcular_scores

log = logging.getLogger(__name__)


def procesar_reporte(
    db: Session,
    reporte_id: str,
    tipo: str,
    animal: str,
    nombre: Optional[str],
    raza: Optional[str],
    color: Optional[str],
    tamano: Optional[str],
    descripcion: Optional[str],
    lat: float,
    lng: float,
    urls_fotos: list[str],
) -> list[Coincidencia]:
    """
    Punto de entrada principal. Llamado por el Kafka consumer
    al recibir un evento ss.reportes.created o ss.reportes.updated.

    Retorna la lista de coincidencias creadas/actualizadas.
    """
    log.info("Procesando matching para reporte %s (%s %s)", reporte_id, tipo, animal)

    # 1. Generar embeddings separados y persistirlos en PostgreSQL
    partes = [p for p in [nombre, raza, color, descripcion] if p]
    texto = " ".join(partes) if partes else "mascota"
    emb_texto = embedding_service.generar_texto_embedding(texto)
    url_foto_principal = urls_fotos[0] if urls_fotos else None
    emb_imagen = embedding_service.generar_imagen_embedding(url_foto_principal) if url_foto_principal else None
    _guardar_embeddings(db, reporte_id, emb_texto, emb_imagen)

    # 2. Buscar candidatos del tipo opuesto, misma especie, estado activo
    tipo_opuesto = "encontrado" if tipo == "perdido" else "perdido"
    candidatos = (
        db.query(Reporte)
        .filter(
            Reporte.tipo == tipo_opuesto,
            Reporte.animal == animal,
            Reporte.estado == "activo",
            Reporte.id != uuid.UUID(reporte_id),
        )
        .all()
    )

    log.info("Candidatos encontrados: %d", len(candidatos))

    # 3. Calcular scores y crear coincidencias
    coincidencias_creadas = []

    for candidato in candidatos:
        emb_texto_candidato = (
            np.array(candidato.embedding_texto, dtype=np.float32)
            if candidato.embedding_texto is not None else None
        )
        emb_imagen_candidato = (
            np.array(candidato.embedding_imagen, dtype=np.float32)
            if candidato.embedding_imagen is not None else None
        )

        scores = calcular_scores(
            nombre_a=nombre, raza_a=raza, color_a=color, tamano_a=tamano,
            descripcion_a=descripcion, lat_a=lat, lng_a=lng,
            emb_texto_a=emb_texto, emb_imagen_a=emb_imagen,
            nombre_b=candidato.nombre, raza_b=candidato.raza, color_b=candidato.color,
            tamano_b=candidato.tamano, descripcion_b=candidato.descripcion,
            lat_b=float(candidato.lat), lng_b=float(candidato.lng),
            emb_texto_b=emb_texto_candidato, emb_imagen_b=emb_imagen_candidato,
        )

        score_total = scores["total"]
        log.debug("Score con candidato %s: %.4f", candidato.id, score_total)

        if score_total >= settings.match_threshold:
            coincidencia = _upsert_coincidencia(
                db, reporte_id, str(candidato.id), tipo, scores
            )
            coincidencias_creadas.append(coincidencia)
            log.info(
                "✅ Match encontrado: %s ↔ %s (score: %.4f)",
                reporte_id, candidato.id, score_total
            )

    db.commit()
    log.info("Matching completado — %d coincidencias generadas", len(coincidencias_creadas))
    return coincidencias_creadas


# ── Privado ────────────────────────────────────────────────────────────────

def _guardar_embeddings(db: Session, reporte_id: str, emb_texto: np.ndarray, emb_imagen: Optional[np.ndarray]) -> None:
    """Actualiza los embeddings separados del reporte en PostgreSQL."""
    reporte = db.query(Reporte).filter(Reporte.id == uuid.UUID(reporte_id)).first()
    if reporte:
        reporte.embedding_texto  = emb_texto.tolist() if emb_texto is not None else None
        reporte.embedding_imagen = emb_imagen.tolist() if emb_imagen is not None else None
        db.flush()
    else:
        log.warning("Reporte %s no encontrado al intentar guardar embeddings", reporte_id)


def _upsert_coincidencia(
    db: Session,
    reporte_nuevo_id: str,
    candidato_id: str,
    tipo_nuevo: str,
    scores: dict,
) -> Coincidencia:
    """
    Crea o actualiza una coincidencia entre dos reportes.
    Siempre almacena (perdido_id, encontrado_id) en el orden correcto.
    """
    if tipo_nuevo == "perdido":
        perdido_id    = uuid.UUID(reporte_nuevo_id)
        encontrado_id = uuid.UUID(candidato_id)
    else:
        perdido_id    = uuid.UUID(candidato_id)
        encontrado_id = uuid.UUID(reporte_nuevo_id)

    # Buscar si ya existe
    existente = (
        db.query(Coincidencia)
        .filter(
            Coincidencia.reporte_perdido_id == perdido_id,
            Coincidencia.reporte_encontrado_id == encontrado_id,
        )
        .first()
    )

    if existente:
        # Actualizar scores si el nuevo es mejor
        if scores["total"] > float(existente.score_total):
            existente.score_total       = scores["total"]
            existente.score_nombre      = scores.get("nombre")
            existente.score_raza        = scores.get("raza")
            existente.score_color       = scores.get("color")
            existente.score_tamano      = scores.get("tamano")
            existente.score_descripcion = scores.get("descripcion")
            existente.score_ubicacion   = scores.get("ubicacion")
            existente.score_imagen      = scores.get("imagen")
        return existente

    # Crear nueva coincidencia
    nueva = Coincidencia(
        id                    = uuid.uuid4(),
        reporte_perdido_id    = perdido_id,
        reporte_encontrado_id = encontrado_id,
        score_total           = scores["total"],
        score_nombre          = scores.get("nombre"),
        score_raza            = scores.get("raza"),
        score_color           = scores.get("color"),
        score_tamano          = scores.get("tamano"),
        score_descripcion     = scores.get("descripcion"),
        score_ubicacion       = scores.get("ubicacion"),
        score_imagen          = scores.get("imagen"),
        estado                = "pendiente",
        notificado_perdido    = False,
        notificado_encontrado = False,
        created_at            = datetime.now(timezone.utc),
    )
    db.add(nueva)
    return nueva
