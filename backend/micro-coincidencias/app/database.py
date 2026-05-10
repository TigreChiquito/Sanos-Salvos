from sqlalchemy import create_engine
from sqlalchemy.orm import sessionmaker, DeclarativeBase
from pgvector.sqlalchemy import Vector  # noqa: F401 — registra el tipo en SQLAlchemy
from app.config import settings

engine = create_engine(
    settings.postgres_url,
    pool_pre_ping=True,
    pool_size=5,
    max_overflow=10,
)

SessionLocal = sessionmaker(autocommit=False, autoflush=False, bind=engine)


class Base(DeclarativeBase):
    pass


def get_db():
    """Dependencia FastAPI para inyectar sesión de DB."""
    db = SessionLocal()
    try:
        yield db
    finally:
        db.close()
