from sqlalchemy import Column, String, Numeric, Boolean, TIMESTAMP, UniqueConstraint
from sqlalchemy.dialects.postgresql import UUID
from app.database import Base


class Coincidencia(Base):
    """Mapea la tabla `coincidencias` de PostgreSQL."""
    __tablename__ = "coincidencias"

    id                    = Column(UUID(as_uuid=True), primary_key=True)
    reporte_perdido_id    = Column(UUID(as_uuid=True), nullable=False)
    reporte_encontrado_id = Column(UUID(as_uuid=True), nullable=False)

    # Scores (0.0 a 1.0)
    score_total       = Column(Numeric(5, 4), nullable=False)
    score_nombre      = Column(Numeric(5, 4))
    score_raza        = Column(Numeric(5, 4))
    score_color       = Column(Numeric(5, 4))
    score_tamano      = Column(Numeric(5, 4))
    score_descripcion = Column(Numeric(5, 4))
    score_ubicacion   = Column(Numeric(5, 4))
    score_imagen      = Column(Numeric(5, 4))

    estado                = Column(String(20), default="pendiente")
    notificado_perdido    = Column(Boolean, default=False)
    notificado_encontrado = Column(Boolean, default=False)
    created_at            = Column(TIMESTAMP(timezone=True))

    __table_args__ = (
        UniqueConstraint("reporte_perdido_id", "reporte_encontrado_id"),
    )
