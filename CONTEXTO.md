# CONTEXTO DEL PROYECTO — Sanos & Salvos
> Archivo de contexto para continuación de conversaciones con IA.
> Última actualización: Mayo 2026.

---

## 1. DESCRIPCIÓN GENERAL

**Sanos & Salvos** es una plataforma comunitaria chilena para reportar y encontrar mascotas perdidas en la Región Metropolitana de Santiago. Los usuarios publican reportes con fotos y ubicación en un mapa interactivo, y el sistema detecta automáticamente posibles coincidencias entre reportes de "perdido" y "encontrado" usando un motor de matching multidimensional con Machine Learning.

**Estado actual: PROYECTO COMPLETO** — Frontend y backend están conectados y listos para ejecución local.

---

## 2. ARQUITECTURA GENERAL

```
Frontend (Astro + React)
        ↓ HTTP (JWT Bearer)
    Orquestador (Spring Cloud Gateway :8080)
    ├── JWT validation (global WebFilter)
    ├── CORS
    └── Circuit Breaker (Resilience4j)
         ├──→ micro-usuarios    :8081  (Spring Boot)
         ├──→ micro-mascotas    :8082  (Spring Boot)
         └──→ micro-coincidencias :8083 (FastAPI)

Infraestructura (Docker):
  PostgreSQL (pgvector) ← fuente de verdad
  MongoDB               ← read model del mapa (CQRS)
  Kafka + Zookeeper     ← bus de eventos
  Debezium              ← CDC: PostgreSQL → Kafka
  MinIO                 ← fotos de mascotas (S3-compatible)
```

### Flujo de autenticación
```
Usuario → /oauth2/authorization/google (gateway)
       → micro-usuarios → Google OAuth2
       → JWT generado (jjwt 0.12.5)
       → Redirect a FRONTEND_URL/acceder?token=JWT
       → Frontend almacena JWT en localStorage (ss_token)
       → Todas las requests llevan Authorization: Bearer <JWT>
       → Gateway valida JWT e inyecta X-User-Id, X-User-Email, X-User-Name
```

### Flujo de creación de reporte
```
POST /api/reportes (multipart/form-data + JWT)
  → micro-mascotas
  → Escribe en PostgreSQL (reporte + fotos en MinIO)
  → Publica evento en Kafka topic: ss.reportes.sync
  → ReporteSyncConsumer (mismo micro) → sincroniza a MongoDB
  → Publica en Kafka topic: ss.reportes.nuevo
  → micro-coincidencias consume → calcula matching con ML
  → Si score ≥ 0.60 → crea coincidencia en PostgreSQL
```

---

## 3. STACK TECNOLÓGICO

### Frontend
| Tecnología | Versión | Uso |
|---|---|---|
| Astro | 5.x | Framework SSG/SSR + páginas |
| React | 18 | Componentes interactivos (MapView) |
| TypeScript | 5.x | Tipado estático |
| Leaflet | 4.x | Mapa interactivo |
| pnpm | — | Package manager |

### Backend — Microservicios Java
| Tecnología | Versión | Uso |
|---|---|---|
| Spring Boot | 3.3.4 | Base de microservicios |
| Java | 21 | Runtime |
| Spring Security + OAuth2 | — | Autenticación Google |
| jjwt | 0.12.5 | Generación/validación JWT |
| Spring Data JPA | — | Acceso a PostgreSQL |
| Spring Data MongoDB | — | Acceso a MongoDB |
| Spring Kafka | — | Productor/consumidor Kafka |
| Spring Cloud Gateway | 2023.0.3 | API Gateway (WebFlux/Netty) |
| Resilience4j | — | Circuit Breaker |
| MinIO Java SDK | — | Almacenamiento de fotos |
| Lombok | — | Boilerplate reduction |
| Springdoc OpenAPI | 2.5.0 | Swagger UI |
| Maven | 3.9 | Build tool |

### Backend — Microservicio Python
| Tecnología | Versión | Uso |
|---|---|---|
| FastAPI | — | API REST + async |
| Python | 3.11 | Runtime |
| sentence-transformers | — | Embeddings de texto (paraphrase-multilingual-mpnet-base-v2) |
| CLIP (openai/clip-vit-base-patch32) | — | Embeddings de imagen |
| pgvector | — | Extensión PostgreSQL para vectores |
| aiokafka | — | Consumidor Kafka async |
| rapidfuzz | — | Similitud fuzzy de strings |
| haversine | — | Distancia geográfica |
| SQLAlchemy + asyncpg | — | Acceso async a PostgreSQL |
| Gunicorn + Uvicorn | — | Servidor ASGI |
| torch (CPU) | — | Backend ML sin CUDA |

### Infraestructura Docker
| Servicio | Imagen | Puerto |
|---|---|---|
| PostgreSQL (pgvector) | pgvector/pgvector:pg16 | 5432 |
| MongoDB | mongo:7 | 27017 |
| Zookeeper | confluentinc/cp-zookeeper:7.6.0 | 2181 |
| Kafka | confluentinc/cp-kafka:7.6.0 | 29092 (host) |
| Debezium | debezium/connect:2.6 | 8083 |
| MinIO | minio/minio:latest | 9000 (API), 9001 (console) |
| micro-usuarios | build local | 8081 |
| micro-mascotas | build local | 8082 |
| micro-coincidencias | build local | 8083 ⚠️ conflicto con Debezium |
| orquestador | build local | 8080 |

> ⚠️ **Puerto 8083**: Debezium y micro-coincidencias comparten el puerto 8083.
> Pendiente resolver: cambiar micro-coincidencias a puerto 8084 en docker-compose.yml y application.yml.

---

## 4. ESTRUCTURA DEL PROYECTO

```
sanos-salvos/
├── .env                           # Variables de entorno del frontend
├── README.md                      # Instalación y ejecución
├── CONTEXTO.md                    # Este archivo
├── astro.config.mjs
├── package.json
├── src/
│   ├── lib/
│   │   ├── api.ts                 # Cliente HTTP central (apiFetch + API_URL)
│   │   └── auth.ts                # Auth JWT real (login, logout, getSession, etc.)
│   ├── pages/
│   │   ├── index.astro            # Landing page
│   │   ├── acceder.astro          # Login/registro via Google OAuth2
│   │   ├── mapa.astro             # Mapa de reportes
│   │   └── reportar.astro         # Formulario de nuevo reporte
│   └── components/
│       ├── MapView.tsx            # Mapa Leaflet + fetch real a /api/reportes
│       ├── NavbarApp.astro        # Navbar con sesión
│       └── Navbar.astro           # Navbar landing
└── backend/
    ├── .env.example               # Plantilla de variables de entorno
    ├── docker-compose.yml         # Toda la infraestructura + microservicios
    ├── postgres/
    │   └── init/
    │       └── 01-schema.sql      # Schema completo con pgvector
    ├── kafka/
    │   └── connectors/
    │       └── debezium-connector.json
    ├── micro-usuarios/            # Spring Boot :8081
    │   ├── Dockerfile
    │   ├── pom.xml
    │   ├── README.md              # Instrucciones Google Cloud Console
    │   └── src/main/java/cl/sanosysalvos/usuarios/
    │       ├── model/Usuario.java
    │       ├── repository/UsuarioRepository.java
    │       ├── dto/ (UsuarioDTO, etc.)
    │       ├── service/UsuarioService.java
    │       ├── controller/UsuarioController.java, AuthController.java
    │       ├── security/
    │       │   ├── JwtService.java
    │       │   ├── JwtAuthFilter.java
    │       │   ├── OAuth2SuccessHandler.java
    │       │   └── SecurityConfig.java
    │       └── kafka/UsuarioEventProducer.java
    ├── micro-mascotas/            # Spring Boot :8082
    │   ├── Dockerfile
    │   ├── pom.xml
    │   ├── README.md
    │   └── src/main/java/cl/sanosysalvos/mascotas/
    │       ├── model/
    │       │   ├── Reporte.java       (JPA → PostgreSQL)
    │       │   ├── Foto.java          (JPA → PostgreSQL)
    │       │   └── ReporteMongo.java  (MongoDB read model)
    │       ├── repository/
    │       │   ├── ReporteRepository.java
    │       │   └── ReporteMongoRepository.java
    │       ├── dto/ (ReporteDTO, CrearReporteRequest, etc.)
    │       ├── config/ (MinioConfig, MongoConfig, KafkaConfig)
    │       ├── service/ReporteService.java  (CQRS write side)
    │       ├── controller/ReporteController.java
    │       └── kafka/
    │           ├── ReporteEventProducer.java
    │           └── ReporteSyncConsumer.java  (CQRS sync → MongoDB)
    ├── micro-coincidencias/       # FastAPI :8083
    │   ├── Dockerfile
    │   ├── requirements.txt
    │   └── app/
    │       ├── main.py
    │       ├── config/settings.py
    │       ├── database/session.py
    │       ├── models/            (SQLAlchemy: Reporte, Coincidencia)
    │       ├── schemas/           (Pydantic)
    │       ├── services/
    │       │   ├── embedding_service.py   (sentence-transformers + CLIP)
    │       │   ├── scoring_service.py     (7 dimensiones de matching)
    │       │   └── matching_service.py    (orquestador)
    │       ├── kafka/
    │       │   ├── consumer.py    (aiokafka, ThreadPoolExecutor)
    │       │   └── producer.py
    │       └── routers/coincidencias.py
    └── orquestador/               # Spring Cloud Gateway :8080
        ├── Dockerfile
        ├── pom.xml
        ├── README.md
        └── src/main/java/cl/sanosysalvos/orquestador/
            ├── config/
            │   ├── SecurityConfig.java   (WebFlux, CORS)
            │   └── JwtGatewayFilter.java (valida JWT, inyecta X-User-*)
            └── controller/FallbackController.java
```

---

## 5. VARIABLES DE ENTORNO

### Frontend (`.env` en raíz del proyecto)
```env
PUBLIC_API_URL=http://localhost:8080
```

### Backend (`backend/.env` — copiar desde `.env.example`)
```env
# PostgreSQL
POSTGRES_DB=sanosysalvos
POSTGRES_USER=ssuser
POSTGRES_PASSWORD=<password>

# MongoDB
MONGO_ROOT_USER=mongoroot
MONGO_ROOT_PASSWORD=<password>
MONGO_DB=sanosysalvos

# MinIO
MINIO_ROOT_USER=minioadmin
MINIO_ROOT_PASSWORD=<password>
MINIO_BUCKET_FOTOS=fotos-mascotas

# Google OAuth2 (configurar en Google Cloud Console)
GOOGLE_CLIENT_ID=<tu-client-id>.apps.googleusercontent.com
GOOGLE_CLIENT_SECRET=<tu-client-secret>
GOOGLE_REDIRECT_URI=http://localhost:8081/login/oauth2/code/google

# JWT
JWT_SECRET=<string-aleatorio-largo-min-256bits>
JWT_EXPIRATION_MS=86400000

# Config adicional
KAFKA_BOOTSTRAP_SERVERS=kafka:9092
APP_ENV=development
FRONTEND_URL=http://localhost:4321

# micro-coincidencias
MATCH_THRESHOLD=0.60
MAX_DISTANCIA_KM=10.0
```

---

## 6. RUTAS DE LA API (a través del gateway :8080)

| Método | Ruta | Auth | Destino |
|---|---|---|---|
| GET | `/oauth2/authorization/google` | No | micro-usuarios |
| GET | `/login/oauth2/code/google` | No | micro-usuarios |
| GET | `/api/auth/me` | JWT | micro-usuarios |
| POST | `/api/auth/logout` | JWT | micro-usuarios |
| GET | `/api/usuarios/{id}` | JWT | micro-usuarios |
| GET | `/api/reportes` | No | micro-mascotas (MongoDB) |
| GET | `/api/reportes/{id}` | No | micro-mascotas |
| POST | `/api/reportes` | JWT | micro-mascotas |
| PATCH | `/api/reportes/{id}/estado` | JWT | micro-mascotas |
| DELETE | `/api/reportes/{id}` | JWT | micro-mascotas |
| GET | `/api/coincidencias/{reporteId}` | JWT | micro-coincidencias |
| GET | `/actuator/health` | No | orquestador |
| GET | `/swagger-ui.html` | No | orquestador |

---

## 7. BASE DE DATOS — PostgreSQL

### Tablas principales
```sql
usuarios          -- id UUID, google_id, nombre, apellido, email, foto_perfil_url
reportes          -- id UUID, usuario_id, tipo, animal, nombre, raza, color,
                  --   tamano, descripcion, lat, lng, estado, embedding vector(512)
fotos             -- id, reporte_id, bucket_key, url, orden
coincidencias     -- id, reporte_perdido_id, reporte_encontrado_id,
                  --   score_total, score_nombre, score_raza, score_color,
                  --   score_tamano, score_descripcion, score_ubicacion, score_imagen
notificaciones    -- id, usuario_id, coincidencia_id, leida
```

### Índices importantes
```sql
-- Búsqueda vectorial de embeddings (pgvector)
CREATE INDEX idx_reportes_embedding
  ON reportes USING ivfflat (embedding vector_cosine_ops) WITH (lists = 100);

-- Búsqueda geoespacial
CREATE INDEX idx_reportes_ubicacion ON reportes (lat, lng);

-- CDC para Debezium
CREATE PUBLICATION ss_publication FOR TABLE usuarios, reportes, fotos, coincidencias, notificaciones;
```

---

## 8. MOTOR DE COINCIDENCIAS (micro-coincidencias)

El matching entre reportes "perdido" y "encontrado" se basa en 7 dimensiones con pesos configurables:

```python
PESOS = {
    "nombre":      0.10,  # rapidfuzz token_sort_ratio
    "raza":        0.20,  # rapidfuzz token_sort_ratio
    "color":       0.15,  # rapidfuzz partial_ratio
    "tamano":      0.10,  # match exacto
    "descripcion": 0.20,  # coseno entre embeddings (sentence-transformer)
    "ubicacion":   0.15,  # haversine < MAX_DISTANCIA_KM
    "imagen":      0.10,  # coseno entre embeddings CLIP
}
```

- Si un campo es None, su peso se redistribuye proporcionalmente entre los demás.
- Si `score_total ≥ MATCH_THRESHOLD (0.60)` → se crea un registro en `coincidencias`.
- El embedding combinado (512D) = 60% text (sentence-transformer) + 40% image (CLIP).
- Los modelos se descargan **durante el build del Docker image** para evitar lentitud en el primer arranque.

---

## 9. AUTENTICACIÓN — DETALLES TÉCNICOS

### Flujo JWT
1. Frontend redirige a `http://localhost:8080/oauth2/authorization/google`
2. Gateway proxya a micro-usuarios (Spring Security OAuth2)
3. `OAuth2SuccessHandler` genera JWT con claims: `sub` (UUID del usuario), `email`, `nombre`, `apellido`
4. Redirect a `FRONTEND_URL/acceder?token=<JWT>`
5. `acceder.astro` llama `login(token)` → GET /api/auth/me → cachea `User` en `ss_user`
6. `apiFetch()` inyecta `Authorization: Bearer <token>` en cada request
7. `JwtGatewayFilter` (orquestador) valida JWT e inyecta headers:
   - `X-User-Id`: UUID del usuario
   - `X-User-Email`: email
   - `X-User-Name`: nombre completo

### localStorage keys
| Key | Contenido |
|---|---|
| `ss_token` | JWT raw string |
| `ss_user` | JSON del objeto User cacheado |

### Rutas públicas (no requieren JWT)
- `GET /api/reportes` y `GET /api/reportes/{id}`
- `/oauth2/**`, `/login/oauth2/**`
- `/fallback/**`, `/actuator/health`

---

## 10. KAFKA TOPICS

| Topic | Productor | Consumidor | Payload |
|---|---|---|---|
| `ss.reportes.sync` | micro-mascotas | micro-mascotas (ReporteSyncConsumer) | ReporteSyncEvent {tipo, payload} |
| `ss.reportes.nuevo` | micro-mascotas | micro-coincidencias | {reporteId, tipo} |
| `ss.coincidencias.creada` | micro-coincidencias | (pendiente: notificaciones) | CoincidenciaEvent |
| `ss.usuarios.nuevo` | micro-usuarios | (pendiente: bienvenida) | UsuarioEvent |
| `ss.public.*` | Debezium (CDC) | — | Cambios raw de PostgreSQL |

---

## 11. CIRCUIT BREAKER — CONFIGURACIÓN

```
CLOSED → OPEN: ≥50% fallos en ventana de 10 llamadas
OPEN → HALF_OPEN: tras 15 segundos de espera
HALF_OPEN → CLOSED: 3 llamadas de prueba exitosas

Timeouts:
  micro-usuarios:       5s
  micro-mascotas:       15s  (uploads de fotos)
  micro-coincidencias:  30s  (inferencia ML)

Fallbacks (HTTP 503):
  /fallback/usuarios
  /fallback/mascotas
  /fallback/coincidencias
```

---

## 12. ESTADO ACTUAL — QUÉ ESTÁ HECHO

### ✅ Completado
- [x] Infraestructura completa Docker Compose (postgres, mongo, kafka, zookeeper, debezium, minio)
- [x] Schema SQL PostgreSQL con pgvector, índices, triggers y CDC publication
- [x] Debezium connector config
- [x] micro-usuarios: OAuth2 Google, JWT, CRUD usuarios, Kafka producer
- [x] micro-mascotas: CQRS (PG write + MongoDB read), MinIO fotos, Kafka producer+consumer
- [x] micro-coincidencias: FastAPI, sentence-transformers, CLIP, pgvector, aiokafka, scoring 7D
- [x] Orquestador: Spring Cloud Gateway, JWT global filter, Circuit Breaker, CORS
- [x] Frontend integrado: api.ts, auth.ts real, acceder.astro (OAuth2), MapView.tsx (fetch real), reportar.astro (POST real)
- [x] Variables de entorno documentadas

### ⚠️ Pendiente / Issues conocidos
- [ ] **Conflicto de puerto 8083**: Debezium y micro-coincidencias usan el mismo puerto. Solución: cambiar micro-coincidencias a 8084 en docker-compose.yml y su application.yml/Dockerfile.
- [ ] Sistema de notificaciones: el topic `ss.coincidencias.creada` existe pero no hay consumer que notifique al usuario.
- [ ] Soft delete en reportes: el estado "eliminado" está modelado pero falta lógica de visibilidad en los queries de MongoDB.
- [ ] Paginación en GET /api/reportes: actualmente devuelve todos los reportes sin paginar (MongoDB).
- [ ] Filtros geoespaciales en el endpoint de reportes: el mapa filtra en cliente, no en servidor.
- [ ] Tests unitarios e integración: no existen aún.
- [ ] CI/CD: no configurado.
- [ ] Producción: variables de entorno, HTTPS, dominios reales.

---

## 13. GOOGLE CLOUD CONSOLE — CONFIGURACIÓN REQUERIDA

Para que el OAuth2 funcione en local:

1. Ir a https://console.cloud.google.com → APIs & Services → Credentials
2. Crear o editar un OAuth2 Client ID (tipo "Web application")
3. Agregar URI de redirección autorizada: `http://localhost:8081/login/oauth2/code/google`
4. Copiar Client ID y Client Secret al `backend/.env`
5. En "OAuth consent screen": agregar tu email como usuario de prueba (mientras esté en modo "Testing")

---

## 14. COMANDOS ÚTILES

```bash
# Levantar todo el backend (infraestructura + microservicios)
cd backend && docker compose up --build

# Solo infraestructura (sin microservicios)
docker compose up postgres mongodb kafka zookeeper debezium minio

# Con herramientas dev (Kafka UI :8080, mongo-express :8081)
docker compose --profile dev up --build

# Registrar conector Debezium (una vez, después de que los servicios estén healthy)
curl -X POST http://localhost:8083/connectors \
  -H "Content-Type: application/json" \
  -d @kafka/connectors/debezium-connector.json

# Ver estado de los connectors
curl http://localhost:8083/connectors

# Levantar frontend
cd .. && npm run dev   # o pnpm dev

# Ver logs de un servicio específico
docker compose logs -f micro-mascotas

# Reiniciar solo un microservicio
docker compose restart micro-coincidencias

# Limpiar todo (volúmenes incluidos)
docker compose down -v
```

---

## 15. PUERTOS DE ACCESO LOCAL

| Servicio | URL |
|---|---|
| Frontend (Astro) | http://localhost:4321 |
| API Gateway | http://localhost:8080 |
| Swagger UI | http://localhost:8080/swagger-ui.html |
| micro-usuarios | http://localhost:8081 |
| micro-mascotas | http://localhost:8082 |
| micro-coincidencias | http://localhost:8083 |
| Debezium REST API | http://localhost:8083 ⚠️ conflicto |
| MinIO Console | http://localhost:9001 |
| MinIO API | http://localhost:9000 |
| Kafka (host) | localhost:29092 |
| PostgreSQL | localhost:5432 |
| MongoDB | localhost:27017 |
| Kafka UI (dev) | http://localhost:8080 ⚠️ conflicto con gateway |
| mongo-express (dev) | http://localhost:8081 ⚠️ conflicto con micro-usuarios |
