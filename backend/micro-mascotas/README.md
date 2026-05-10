# micro-mascotas

Microservicio de Gestión de Mascotas — Spring Boot 3.3 + Java 21.

Implementa el patrón **CQRS**:
- **Escritura** → PostgreSQL (fuente de verdad)
- **Lectura** → MongoDB (optimizado para queries del mapa)
- **Sincronización** → Kafka (ReporteEventProducer → ReporteSyncConsumer)

---

## Endpoints

| Método | Ruta | Auth | Descripción |
|---|---|---|---|
| `GET` | `/api/reportes` | No | Listar reportes activos (filtros: tipo, animal, bounds) |
| `GET` | `/api/reportes/{id}` | No | Detalle de un reporte |
| `POST` | `/api/reportes` | JWT | Crear reporte con fotos (multipart/form-data) |
| `PATCH` | `/api/reportes/{id}/estado` | JWT | Cambiar estado (activo/resuelto) |
| `DELETE` | `/api/reportes/{id}` | JWT | Eliminar reporte (solo autor) |

Swagger UI: [http://localhost:8082/swagger-ui.html](http://localhost:8082/swagger-ui.html)

---

## Formato de creación de reporte (multipart/form-data)

```
POST /api/reportes
Content-Type: multipart/form-data

datos (JSON):
{
  "tipo": "perdido",
  "animal": "perro",
  "nombre": "Firulais",
  "raza": "Labrador",
  "color": "amarillo",
  "tamano": "grande",
  "descripcion": "Collar azul, muy amistoso",
  "lat": -33.4489,
  "lng": -70.6693,
  "zona": "Santiago Centro"
}

fotos: [archivo1.jpg, archivo2.jpg] (máx 5, cada una máx 10MB)
```

---

## Flujo CQRS

```
POST /api/reportes
  → ReporteService.crear()
    → Escribe en PostgreSQL (Reporte + Foto)
    → Sube fotos a MinIO
    → ReporteEventProducer.publishCreated() → Kafka [ss.reportes.sync]
                                             → Kafka [ss.reportes.created]  ← Micro 3 escucha
  ← ReporteSyncConsumer (mismo servicio)
    → Escribe en MongoDB

GET /api/reportes
  → ReporteService.listar()
    → Lee desde MongoDB (rápido, sin joins)
```

---

## Variables de entorno requeridas

| Variable | Descripción |
|---|---|
| `POSTGRES_URL` | URL JDBC de PostgreSQL |
| `POSTGRES_USER` / `POSTGRES_PASSWORD` | Credenciales PG |
| `MONGO_URL` | URI de MongoDB |
| `KAFKA_BOOTSTRAP_SERVERS` | Brokers de Kafka |
| `MINIO_ENDPOINT` | URL de MinIO (ej: http://minio:9000) |
| `MINIO_ROOT_USER` / `MINIO_ROOT_PASSWORD` | Credenciales MinIO |
| `MINIO_BUCKET_FOTOS` | Nombre del bucket (default: fotos-mascotas) |
| `JWT_SECRET` | Mismo secret que usa micro-usuarios |
