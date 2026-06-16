from sqlalchemy import Column, String, Numeric, Text, Boolean, TIMESTAMP
from sqlalchemy.dialects.postgresql import UUID
from pgvector.sqlalchemy import Vector
from app.database import Base


class Reporte(Base):
    """
    Mapea la tabla `reportes` de PostgreSQL (solo lectura desde este micro).
    El Motor de Coincidencias lee los reportes para calcular matches y
    escribe únicamente el campo `embedding` tras procesar texto e imágenes.
    """
    __tablename__ = "reportes"

    id          = Column(UUID(as_uuid=True), primary_key=True)
    tipo        = Column(String(20), nullable=False)   # perdido | encontrado
    animal      = Column(String(20), nullable=False)
    estado      = Column(String(20), nullable=False, default="activo")
    nombre      = Column(String(100))
    raza        = Column(String(100))
    color       = Column(String(100))
    tamano      = Column(String(20))
    descripcion = Column(Text)
    lat         = Column(Numeric(10, 8), nullable=False)
    lng         = Column(Numeric(11, 8), nullable=False)
    zona        = Column(String(255))
    usuario_id  = Column(UUID(as_uuid=True), nullable=False)

    embedding         = Column(Vector(512))   # combinado (texto 60% + imagen 40%)
    embedding_texto   = Column(Vector(768))   # sentence-transformers puro → score_descripcion
    embedding_imagen  = Column(Vector(512))   # CLIP puro                 → score_imagen

    created_at  = Column(TIMESTAMP(timezone=True))
    updated_at  = Column(TIMESTAMP(timezone=True))
