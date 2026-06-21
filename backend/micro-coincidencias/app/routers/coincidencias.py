"""
Endpoints REST del Motor de Coincidencias.

GET  /api/coincidencias                     → listar todas (filtros opcionales)
GET  /api/coincidencias/{reporte_id}        → coincidencias de un reporte
POST /api/coincidencias/recalcular/{id}     → forzar re-matching de un reporte
POST /api/coincidencias/reanalizar-todos    → re-analizar todos los reportes activos
PATCH /api/coincidencias/{id}               → actualizar estado (confirmado/descartado)
"""
import asyncio
import logging
import uuid
from concurrent.futures import ThreadPoolExecutor

from fastapi import APIRouter, Depends, HTTPException, BackgroundTasks
from sqlalchemy.orm import Session

from app.database import get_db, SessionLocal
from app.kafka.producer import publicar_coincidencia
from app.models.coincidencia import Coincidencia
from app.models.reporte import Reporte
from app.schemas.coincidencia import CoincidenciaOut, CoincidenciaEstadoUpdate
from app.services import matching_service

log = logging.getLogger(__name__)
router = APIRouter(prefix="/api/coincidencias", tags=["Coincidencias"])

_executor = ThreadPoolExecutor(max_workers=2)


# ── Helpers ────────────────────────────────────────────────────────────────

def _run_matching_sync(reporte: Reporte) -> list[dict]:
    """Ejecuta el matching de un reporte en un hilo separado. Retorna dicts puros."""
    db = SessionLocal()
    try:
        return matching_service.procesar_reporte(
            db=db,
            reporte_id=str(reporte.id),
            usuario_id=str(reporte.usuario_id),
            tipo=reporte.tipo,
            animal=reporte.animal,
            nombre=reporte.nombre,
            raza=reporte.raza,
            color=reporte.color,
            tamano=reporte.tamano,
            descripcion=reporte.descripcion,
            lat=float(reporte.lat),
            lng=float(reporte.lng),
            urls_fotos=[],
        )
    except Exception as e:
        log.error("Error en matching para reporte %s: %s", reporte.id, e, exc_info=True)
        return []
    finally:
        db.close()


async def _publicar_items(items: list[dict], reporte_id: str):
    for item in items:
        try:
            await publicar_coincidencia(item)
        except Exception as e:
            log.error("Error publicando coincidencia de reporte %s: %s", reporte_id, e)


# ── Endpoints ──────────────────────────────────────────────────────────────

@router.get("", response_model=list[CoincidenciaOut])
def listar(
    estado: str | None = None,
    min_score: float = 0.0,
    db: Session = Depends(get_db),
):
    """Lista todas las coincidencias con filtros opcionales."""
    query = db.query(Coincidencia).filter(Coincidencia.score_total >= min_score)
    if estado:
        query = query.filter(Coincidencia.estado == estado)
    return query.order_by(Coincidencia.score_total.desc()).all()


@router.get("/{reporte_id}", response_model=list[CoincidenciaOut])
def por_reporte(reporte_id: uuid.UUID, db: Session = Depends(get_db)):
    """Retorna todas las coincidencias asociadas a un reporte."""
    return (
        db.query(Coincidencia)
        .filter(
            (Coincidencia.reporte_perdido_id == reporte_id)
            | (Coincidencia.reporte_encontrado_id == reporte_id)
        )
        .order_by(Coincidencia.score_total.desc())
        .all()
    )


@router.post("/recalcular/{reporte_id}", status_code=202)
async def recalcular(
    reporte_id: uuid.UUID,
    background_tasks: BackgroundTasks,
    db: Session = Depends(get_db),
):
    """Fuerza el re-matching de un reporte específico y publica los matches en Kafka."""
    reporte = db.query(Reporte).filter(Reporte.id == reporte_id).first()
    if not reporte:
        raise HTTPException(status_code=404, detail="Reporte no encontrado")

    # Capturar datos antes de que la sesión de la request se cierre
    reporte_snapshot = {
        "id":          str(reporte.id),
        "usuario_id":  str(reporte.usuario_id),
        "tipo":        reporte.tipo,
        "animal":      reporte.animal,
        "nombre":      reporte.nombre,
        "raza":        reporte.raza,
        "color":       reporte.color,
        "tamano":      reporte.tamano,
        "descripcion": reporte.descripcion,
        "lat":         float(reporte.lat),
        "lng":         float(reporte.lng),
    }

    async def _task():
        loop = asyncio.get_running_loop()
        items = await loop.run_in_executor(
            _executor,
            lambda: _run_matching_sync_from_dict(reporte_snapshot),
        )
        await _publicar_items(items, reporte_snapshot["id"])
        log.info("Re-matching completado para %s: %d coincidencias", reporte_snapshot["id"], len(items))

    background_tasks.add_task(_task)
    return {"message": "Re-matching iniciado", "reporte_id": str(reporte_id)}


@router.post("/reanalizar-todos", status_code=202)
async def reanalizar_todos(background_tasks: BackgroundTasks):
    """
    Re-analiza TODOS los reportes activos en busca de coincidencias.
    Útil después de ajustar pesos o al arrancar con reportes pre-existentes.
    Retorna inmediatamente; el trabajo ocurre en background.
    """
    # Leer los reportes en la sesión de la request y serializar a dicts
    db = SessionLocal()
    try:
        reportes = (
            db.query(Reporte)
            .filter(Reporte.estado == "activo")
            .all()
        )
        snapshots = [
            {
                "id":          str(r.id),
                "usuario_id":  str(r.usuario_id),
                "tipo":        r.tipo,
                "animal":      r.animal,
                "nombre":      r.nombre,
                "raza":        r.raza,
                "color":       r.color,
                "tamano":      r.tamano,
                "descripcion": r.descripcion,
                "lat":         float(r.lat),
                "lng":         float(r.lng),
            }
            for r in reportes
        ]
    finally:
        db.close()

    total = len(snapshots)
    log.info("Re-análisis total iniciado — %d reportes activos", total)

    async def _task():
        loop = asyncio.get_running_loop()
        matches_total = 0
        for snap in snapshots:
            items = await loop.run_in_executor(
                _executor,
                lambda s=snap: _run_matching_sync_from_dict(s),
            )
            await _publicar_items(items, snap["id"])
            matches_total += len(items)

        log.info(
            "Re-análisis total completado — %d reportes procesados, %d coincidencias generadas",
            total, matches_total,
        )

    background_tasks.add_task(_task)
    return {"message": "Re-análisis iniciado", "reportes": total}


@router.patch("/{coincidencia_id}", response_model=CoincidenciaOut)
def actualizar_estado(
    coincidencia_id: uuid.UUID,
    body: CoincidenciaEstadoUpdate,
    db: Session = Depends(get_db),
):
    """Actualiza el estado de una coincidencia (confirmado / descartado)."""
    estados_validos = {"confirmado", "descartado", "pendiente"}
    if body.estado not in estados_validos:
        raise HTTPException(
            status_code=400,
            detail=f"Estado inválido. Debe ser uno de: {estados_validos}",
        )

    coincidencia = db.query(Coincidencia).filter(Coincidencia.id == coincidencia_id).first()
    if not coincidencia:
        raise HTTPException(status_code=404, detail="Coincidencia no encontrada")

    coincidencia.estado = body.estado
    db.commit()
    db.refresh(coincidencia)
    return coincidencia


# ── Privado ────────────────────────────────────────────────────────────────

def _run_matching_sync_from_dict(snap: dict) -> list[dict]:
    """Versión de _run_matching_sync que acepta un dict serializado."""
    db = SessionLocal()
    try:
        return matching_service.procesar_reporte(
            db=db,
            reporte_id=snap["id"],
            usuario_id=snap["usuario_id"],
            tipo=snap["tipo"],
            animal=snap["animal"],
            nombre=snap["nombre"],
            raza=snap["raza"],
            color=snap["color"],
            tamano=snap["tamano"],
            descripcion=snap["descripcion"],
            lat=snap["lat"],
            lng=snap["lng"],
            urls_fotos=[],
        )
    except Exception as e:
        log.error("Error en matching para reporte %s: %s", snap["id"], e, exc_info=True)
        return []
    finally:
        db.close()
