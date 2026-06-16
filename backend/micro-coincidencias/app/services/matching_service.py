"""
Orquestador del Motor de Coincidencias.

Flujo por cada nuevo reporte:
  1. Generar embedding combinado (texto + imagen) → guardar en PostgreSQL
  2. Buscar candidatos del tipo opuesto (perdido ↔ encontrado), misma especie
  3. Calcular scores contra cada candidato
  4. Si score_total >= threshold → crear/actualizar coincidencia en PostgreSQL
  5. Retornar lista de coincidencias encontradas para que el producer las publique
"""
import logging
import time
import uuid
from datetime import datetime, timezone
from typing import Optional

import numpy as np
from sqlalchemy.orm import Session

from app.config import settings
from app.metrics import (
    COINCIDENCIA_SCORE, COINCIDENCIAS_ENCONTRADAS,
    MATCHING_CANDIDATOS, MATCHING_DURACION, MATCHING_PROCESADOS,
)
from app.models.coincidencia import Coincidencia
from app.models.reporte import Reporte
from app.services.embedding_service import embedding_service
from app.services.scoring_service import calcular_scores

log = logging.getLogger(__name__)


def procesar_reporte(
    db: Session,
    reporte_id: str,
    usuario_id: str,
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
) -> list[dict]:
    """
    Punto de entrada principal. Llamado por el Kafka consumer
    al recibir un evento ss.reportes.created o ss.reportes.updated.

    Retorna la lista de coincidencias creadas/actualizadas.
    """
    log.info("Procesando matching para reporte %s (%s %s)", reporte_id, tipo, animal)
    MATCHING_PROCESADOS.inc()
    t0 = time.perf_counter()

    # 1. Generar embeddings y persistirlos en PostgreSQL
    url_foto_principal = urls_fotos[0] if urls_fotos else None
    emb_texto, emb_imagen, emb_combinado = embedding_service.generar_todos_embeddings(
        nombre, raza, color, descripcion, url_foto_principal
    )
    _guardar_embedding(db, reporte_id, emb_texto, emb_imagen, emb_combinado)

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
    MATCHING_CANDIDATOS.observe(len(candidatos))

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
            nombre_a=nombre,
            raza_a=raza,
            color_a=color,
            tamano_a=tamano,
            descripcion_a=descripcion,
            lat_a=lat,
            lng_a=lng,
            embedding_texto_a=emb_texto,
            embedding_imagen_a=emb_imagen,
            nombre_b=candidato.nombre,
            raza_b=candidato.raza,
            color_b=candidato.color,
            tamano_b=candidato.tamano,
            descripcion_b=candidato.descripcion,
            lat_b=float(candidato.lat),
            lng_b=float(candidato.lng),
            embedding_texto_b=emb_texto_candidato,
            embedding_imagen_b=emb_imagen_candidato,
        )

        score_total = scores["total"]
        log.debug("Score con candidato %s: %.4f", candidato.id, score_total)

        if score_total >= settings.match_threshold:
            coincidencia = _upsert_coincidencia(
                db, reporte_id, str(candidato.id), tipo, scores
            )
            if tipo == "perdido":
                uid_perdido    = usuario_id
                uid_encontrado = str(candidato.usuario_id)
            else:
                uid_perdido    = str(candidato.usuario_id)
                uid_encontrado = usuario_id

            # Serializar a dict puro ANTES de cerrar la sesión DB
            # (los objetos ORM quedan "detached" después de db.close())
            coincidencias_creadas.append({
                "coincidencia_id":       str(coincidencia.id),
                "reporte_perdido_id":    str(coincidencia.reporte_perdido_id),
                "reporte_encontrado_id": str(coincidencia.reporte_encontrado_id),
                "score_total":           float(coincidencia.score_total),
                "estado":                coincidencia.estado,
                "usuario_perdido_id":    uid_perdido,
                "usuario_encontrado_id": uid_encontrado,
            })
            COINCIDENCIAS_ENCONTRADAS.inc()
            COINCIDENCIA_SCORE.observe(score_total)
            log.info(
                "✅ Match encontrado: %s ↔ %s (score: %.4f)",
                reporte_id, candidato.id, score_total
            )

    db.commit()
    MATCHING_DURACION.observe(time.perf_counter() - t0)
    log.info("Matching completado — %d coincidencias generadas", len(coincidencias_creadas))
    return coincidencias_creadas  # list[dict] con keys serializadas (sin ORM objects)


def solo_matching(
    db: Session,
    reporte_id: str,
    usuario_id: str,
    tipo: str,
    animal: str,
    nombre: Optional[str],
    raza: Optional[str],
    color: Optional[str],
    tamano: Optional[str],
    descripcion: Optional[str],
    lat: float,
    lng: float,
) -> list[dict]:
    """
    Ejecuta solo el matching sin regenerar embeddings.
    Los embeddings del reporte deben estar ya en la DB (uso en Fase 2 de reanalizar-todos).
    Actualiza la coincidencia siempre (no solo si el score es mayor) para reflejar
    los embeddings completos disponibles tras la Fase 1.
    """
    reporte_db = db.query(Reporte).filter(Reporte.id == uuid.UUID(reporte_id)).first()
    if not reporte_db:
        log.warning("Reporte %s no encontrado para solo_matching", reporte_id)
        return []

    emb_texto = (
        np.array(reporte_db.embedding_texto, dtype=np.float32)
        if reporte_db.embedding_texto is not None else None
    )
    emb_imagen = (
        np.array(reporte_db.embedding_imagen, dtype=np.float32)
        if reporte_db.embedding_imagen is not None else None
    )

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

    coincidencias_creadas = []
    for candidato in candidatos:
        emb_texto_c = (
            np.array(candidato.embedding_texto, dtype=np.float32)
            if candidato.embedding_texto is not None else None
        )
        emb_imagen_c = (
            np.array(candidato.embedding_imagen, dtype=np.float32)
            if candidato.embedding_imagen is not None else None
        )

        scores = calcular_scores(
            nombre_a=nombre, raza_a=raza, color_a=color, tamano_a=tamano,
            descripcion_a=descripcion, lat_a=lat, lng_a=lng,
            embedding_texto_a=emb_texto, embedding_imagen_a=emb_imagen,
            nombre_b=candidato.nombre, raza_b=candidato.raza,
            color_b=candidato.color, tamano_b=candidato.tamano,
            descripcion_b=candidato.descripcion,
            lat_b=float(candidato.lat), lng_b=float(candidato.lng),
            embedding_texto_b=emb_texto_c, embedding_imagen_b=emb_imagen_c,
        )

        if scores["total"] >= settings.match_threshold:
            coincidencia = _upsert_coincidencia_forzado(
                db, reporte_id, str(candidato.id), tipo, scores
            )
            uid_perdido = usuario_id if tipo == "perdido" else str(candidato.usuario_id)
            uid_encontrado = str(candidato.usuario_id) if tipo == "perdido" else usuario_id
            coincidencias_creadas.append({
                "coincidencia_id":       str(coincidencia.id),
                "reporte_perdido_id":    str(coincidencia.reporte_perdido_id),
                "reporte_encontrado_id": str(coincidencia.reporte_encontrado_id),
                "score_total":           float(coincidencia.score_total),
                "estado":                coincidencia.estado,
                "usuario_perdido_id":    uid_perdido,
                "usuario_encontrado_id": uid_encontrado,
            })
            log.info("✅ Match (fase 2): %s ↔ %s (score: %.4f)", reporte_id, candidato.id, scores["total"])

    db.commit()
    return coincidencias_creadas


# ── Privado ────────────────────────────────────────────────────────────────

def _guardar_embedding(
    db: Session,
    reporte_id: str,
    emb_texto: np.ndarray,
    emb_imagen: Optional[np.ndarray],
    emb_combinado: np.ndarray,
) -> None:
    """Persiste los tres embeddings del reporte en PostgreSQL."""
    reporte = db.query(Reporte).filter(Reporte.id == uuid.UUID(reporte_id)).first()
    if reporte:
        reporte.embedding        = emb_combinado.tolist()
        reporte.embedding_texto  = emb_texto.tolist()
        reporte.embedding_imagen = emb_imagen.tolist() if emb_imagen is not None else None
        db.flush()
    else:
        log.warning("Reporte %s no encontrado al intentar guardar embedding", reporte_id)


def _upsert_coincidencia_forzado(
    db: Session,
    reporte_nuevo_id: str,
    candidato_id: str,
    tipo_nuevo: str,
    scores: dict,
) -> "Coincidencia":
    """
    Igual que _upsert_coincidencia pero siempre actualiza TODOS los scores,
    sin importar si el total es mayor. Usado en la Fase 2 de reanalizar-todos
    donde los embeddings ya están completos para ambos extremos.
    """
    if tipo_nuevo == "perdido":
        perdido_id    = uuid.UUID(reporte_nuevo_id)
        encontrado_id = uuid.UUID(candidato_id)
    else:
        perdido_id    = uuid.UUID(candidato_id)
        encontrado_id = uuid.UUID(reporte_nuevo_id)

    existente = (
        db.query(Coincidencia)
        .filter(
            Coincidencia.reporte_perdido_id == perdido_id,
            Coincidencia.reporte_encontrado_id == encontrado_id,
        )
        .first()
    )

    if existente:
        existente.score_total       = scores["total"]
        existente.score_nombre      = scores.get("nombre")
        existente.score_raza        = scores.get("raza")
        existente.score_color       = scores.get("color")
        existente.score_tamano      = scores.get("tamano")
        existente.score_descripcion = scores.get("descripcion")
        existente.score_ubicacion   = scores.get("ubicacion")
        existente.score_imagen      = scores.get("imagen")
        return existente

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
