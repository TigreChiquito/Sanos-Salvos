from pydantic_settings import BaseSettings, SettingsConfigDict


class Settings(BaseSettings):
    model_config = SettingsConfigDict(env_file=".env", extra="ignore")

    # ── PostgreSQL ────────────────────────────────────────────
    postgres_url: str = "postgresql://ss_admin:changeme@localhost:5432/sanos_salvos"

    # ── Kafka ─────────────────────────────────────────────────
    kafka_bootstrap_servers: str = "localhost:29092"
    kafka_group_id: str = "ss-coincidencias"
    kafka_topic_reportes_created: str = "ss.reportes.created"
    kafka_topic_reportes_updated: str = "ss.reportes.updated"
    kafka_topic_coincidencias: str = "ss.coincidencias.found"

    # ── Motor de Coincidencias ────────────────────────────────
    # Score mínimo para considerar un match válido
    match_threshold: float = 0.60

    # Radio máximo de búsqueda geográfica en km
    max_distancia_km: float = 10.0

    # Modelos ML
    texto_model_name: str = "paraphrase-multilingual-mpnet-base-v2"

    # ── API ───────────────────────────────────────────────────
    app_name: str = "micro-coincidencias"
    app_port: int = 8084
    debug: bool = False


settings = Settings()
