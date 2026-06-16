from sqlalchemy import Column, Integer, Text, TIMESTAMP
from sqlalchemy.dialects.postgresql import UUID
from app.database import Base


class Foto(Base):
    __tablename__ = "fotos"

    id         = Column(UUID(as_uuid=True), primary_key=True)
    reporte_id = Column(UUID(as_uuid=True), nullable=False)
    url        = Column(Text, nullable=False)
    bucket_key = Column(Text, nullable=False)
    orden      = Column(Integer, default=0)
    created_at = Column(TIMESTAMP(timezone=True))
