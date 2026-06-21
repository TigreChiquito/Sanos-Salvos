# micro-coincidencias

Motor de Coincidencias — FastAPI + Python 3.11.

Compara reportes de mascotas perdidas y encontradas usando **7 dimensiones** para determinar si se trata del mismo animal.

---

## Algoritmo de matching

| Dimensión | Técnica | Peso |
|---|---|---|
| Nombre | Fuzzy match (rapidfuzz token_sort) | 10% |
| Raza | Fuzzy match (rapidfuzz token_sort) | 20% |
| Color | Fuzzy partial match | 15% |
| Tamaño | Match exacto binario | 10% |
| Descripción | Coseno entre embeddings sentence-transformers | 20% |
| Ubicación | Distancia Haversine inversa (radio máx: 10km) | 15% |
| Imagen | Coseno entre embeddings CLIP ViT-B/32 | 10% |

> Si un campo está vacío en uno o ambos reportes, su peso se redistribuye proporcionalmente entre las dimensiones disponibles.

**Threshold:** score_total ≥ 0.60 → se crea una coincidencia

---

## Flujo

```
Kafka [ss.reportes.created]
  → consumer.py
    → matching_service.procesar_reporte()
      → embedding_service: genera vector 512D (texto 60% + imagen 40%)
      → Guarda embedding en PostgreSQL (columna vector(512) con pgvector)
      → Busca candidatos del tipo opuesto (perdido ↔ encontrado)
      → scoring_service: calcula scores por dimensión
      → Si score_total ≥ 0.60 → crea Coincidencia en PostgreSQL
    → producer.py → Kafka [ss.coincidencias.found]
```

---

## Modelos ML

| Modelo | Uso | Tamaño |
|---|---|---|
| `paraphrase-multilingual-mpnet-base-v2` | Embeddings de texto en español | ~420MB |
| `CLIP ViT-B/32` | Embeddings de imagen | ~340MB |

Ambos modelos se descargan durante el `docker build` y quedan cacheados en la imagen.

---

## Endpoints

| Método | Ruta | Descripción |
|---|---|---|
| `GET` | `/api/coincidencias` | Listar coincidencias (filtros: estado, min_score) |
| `GET` | `/api/coincidencias/{reporte_id}` | Coincidencias de un reporte |
| `POST` | `/api/coincidencias/recalcular/{reporte_id}` | Forzar re-matching |
| `PATCH` | `/api/coincidencias/{id}` | Actualizar estado (confirmado/descartado) |
| `GET` | `/health` | Health check |

Swagger UI: [http://localhost:8083/docs](http://localhost:8083/docs)

---

## Variables de entorno

| Variable | Descripción |
|---|---|
| `POSTGRES_URL` | URL completa de PostgreSQL |
| `KAFKA_BOOTSTRAP_SERVERS` | Brokers de Kafka |
| `MATCH_THRESHOLD` | Score mínimo para crear coincidencia (default: 0.60) |
| `MAX_DISTANCIA_KM` | Radio máximo geográfico (default: 10.0) |

---

## ⚠️ Nota sobre el tamaño de la imagen Docker

La imagen incluye PyTorch CPU + dos modelos ML (~800MB de modelos). El build inicial toma ~10 minutos pero las capas quedan cacheadas. Recomendado usar un registry privado para evitar re-descargas en cada deploy.
