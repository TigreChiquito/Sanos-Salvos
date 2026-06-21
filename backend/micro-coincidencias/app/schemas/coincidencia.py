from pydantic import BaseModel, UUID4
from typing import Optional
from datetime import datetime


class CoincidenciaOut(BaseModel):
    id: UUID4
    reporte_perdido_id: UUID4
    reporte_encontrado_id: UUID4
    score_total: float
    score_nombre: Optional[float]
    score_raza: Optional[float]
    score_color: Optional[float]
    score_tamano: Optional[float]
    score_descripcion: Optional[float]
    score_ubicacion: Optional[float]
    score_imagen: Optional[float]
    estado: str
    notificado_perdido: bool
    notificado_encontrado: bool
    created_at: Optional[datetime]

    model_config = {"from_attributes": True}


class CoincidenciaEstadoUpdate(BaseModel):
    estado: str  # "confirmado" | "descartado"
