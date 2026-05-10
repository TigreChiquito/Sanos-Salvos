"""
Endpoints REST del Motor de Coincidencias.

GET  /api/coincidencias                     → listar todas (filtros opcionales)
GET  /api/coincidencias/{reporte_id}        → coincidencias de un reporte
POST /api/coincidencias/recalcular/{id}     → forzar re-matching de un reporte
PATCH /api/coincidencias/{id}               → actualizar estado (confirmado/descartado)
"""
import logging
import uuid

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


@router.get("", response_model=list[CoincidenciaOut])
def listar(
    estado: str | None = None,
    min_score: float = 0.0,
    db: Session = Depends(get_db),
):
    """Lista todas las coincidencias con filtros opcionales."""
    query = db.query(Coincidencia).filter(
        Coincidencia.score_total >= min_score
    )
    if estado:
        query = query.filter(Coincidencia.estado == estado)

    return query.order_by(Coincidencia.score_total.desc()).all()


@router.get("/{reporte_id}", response_model=list[CoincidenciaOut])
def por_reporte(reporte_id: uuid.UUID, db: Session = Depends(get_db)):
    """Retorna todas las coincidencias asociadas a un reporte (como perdido o encontrado)."""
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
def recalcular(
    reporte_id: uuid.UUID,
    background_tasks: BackgroundTasks,
    db: Session = Depends(get_db),
):
    """
    Fuerza el re-matching de un reporte.
    Útil cuando se suben nuevas fotos o se edita la descripción.
    El procesamiento ocurre en background para no bloquear la respuesta.
    """
    reporte = db.query(Reporte).filter(Reporte.id == reporte_id).first()
    if not reporte:
        raise HTTPException(status_code=404, detail="Reporte no encontrado")

    def _run():
        _db = SessionLocal()
        try:
            coincidencias = matching_service.procesar_reporte(
                db=_db,
                reporte_id=str(reporte.id),
                tipo=reporte.tipo,
                animal=reporte.animal,
                nombre=reporte.nombre,
                raza=reporte.raza,
                color=reporte.color,
                tamano=reporte.tamano,
                descripcion=reporte.descripcion,
                lat=float(reporte.lat),
                lng=float(reporte.lng),
                urls_fotos=[],  # el re-matching usa el embedding ya guardado
            )
            log.info("Re-matching completado para %s: %d coincidencias", reporte_id, len(coincidencias))
        finally:
            _db.close()

    background_tasks.add_task(_run)
    return {"message": "Re-matching iniciado", "reporte_id": str(reporte_id)}


@router.patch("/{coincidencia_id}", response_model=CoincidenciaOut)
def actualizar_estado(
    coincidencia_id: uuid.UUID,
    body: CoincidenciaEstadoUpdate,
    db: Session = Depends(get_db),
):
    """
    Actualiza el estado de una coincidencia (confirmado / descartado).
    Llamado cuando el usuario acepta o rechaza un match sugerido.
    """
    estados_validos = {"confirmado", "descartado", "pendiente"}
    if body.estado not in estados_validos:
        raise HTTPException(
            status_code=400,
            detail=f"Estado inválido. Debe ser uno de: {estados_validos}"
        )

    coincidencia = db.query(Coincidencia).filter(
        Coincidencia.id == coincidencia_id
    ).first()

    if not coincidencia:
        raise HTTPException(status_code=404, detail="Coincidencia no encontrada")

    coincidencia.estado = body.estado
    db.commit()
    db.refresh(coincidencia)
    return coincidencia
