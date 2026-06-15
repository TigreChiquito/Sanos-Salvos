# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Project Overview

**Sanos & Salvos** is a community platform for reporting and matching lost/found pets in the Santiago Metropolitan Region. It uses AI-powered 7-dimensional scoring (text embeddings + CLIP image similarity) to match reports automatically.

## Development Commands

### Frontend (Astro + React, at repo root)
```bash
pnpm dev          # Start dev server at http://localhost:4321
pnpm build        # Production build to /dist
pnpm preview      # Preview production build
```

### Backend (Docker Compose)
```bash
# From /backend directory
docker compose up -d                        # Start all infrastructure + services
docker compose --profile dev up -d          # Include dev tools (kafka-ui, mongo-express)
docker compose up -d micro-usuarios         # Start a single service
docker compose logs -f micro-mascotas       # Tail logs for a service
docker compose down -v                      # Stop all and remove volumes
```

### Individual Java microservices (from their directory)
```bash
mvn spring-boot:run     # Run locally (requires running infrastructure)
mvn test                # Run tests
mvn package -DskipTests # Build JAR
```

### Python microservice (micro-coincidencias)
```bash
pip install -r requirements.txt
uvicorn app.main:app --reload --port 8084   # Run locally
pytest                                       # Run tests
```

## Architecture

### Service Map
```
Browser
  └── Frontend (Astro/React :4321)
        └── Orquestador / Spring Cloud Gateway (:8080)
              ├── micro-usuarios (:8081)   — Auth, JWT, user profiles
              ├── micro-mascotas (:8082)   — Reports, photos (CQRS)
              └── micro-coincidencias (:8084) — ML matching engine (FastAPI)
```

### Infrastructure (all in `backend/docker-compose.yml`)
| Service | Port | Purpose |
|---|---|---|
| PostgreSQL (pgvector) | 5432 | Source of truth (with 512-dim vector columns) |
| MongoDB | 27017 | Read model (denormalized for map queries) |
| Kafka | 9092/29092 | Async event bus between microservices |
| Debezium | 8083 | CDC: PostgreSQL → Kafka topics |
| MinIO | 9000/9001 | Object storage for pet photos (S3-compatible) |

### Data Flow
1. User submits report → `micro-mascotas` writes to PostgreSQL + uploads photo to MinIO
2. Debezium detects PostgreSQL change → publishes to Kafka topic `ss.public.reportes`
3. `micro-coincidencias` consumes the event → runs 7-dimensional matching → stores results
4. MongoDB read model is updated via Kafka → serves fast map queries

### CQRS Pattern (micro-mascotas)
- **Write side:** PostgreSQL via Spring Data JPA
- **Read side:** MongoDB via Spring Data MongoDB
- **Sync:** Kafka consumer in `micro-mascotas` listens to Debezium events and upserts MongoDB

### Orquestador (Spring Cloud Gateway)
- Reactive WebFlux — **not servlet-based**; avoid blocking APIs here
- JWT validation via `JwtGatewayFilter` before routing to downstream services
- Public paths (no auth): `/api/auth/**`, `/api/actuator/**`, `/swagger-ui/**`, `/v3/api-docs/**`
- Circuit breaker with Resilience4j; fallback at `/fallback`

### ML Matching (micro-coincidencias)
- **Text:** `paraphrase-multilingual-mpnet-base-v2` (sentence-transformers) for breed/description/color
- **Image:** CLIP `ViT-B/32` for visual similarity
- 7 scoring dimensions with weights: breed 20%, description 20%, color 15%, location 15%, name 10%, size 10%, image 10%
- Match registered when total score ≥ 0.60
- Models are pre-downloaded at Docker image build time (not at runtime)

### Frontend Auth (`src/lib/auth.ts` + `src/lib/api.ts`)
- Google OAuth2 → handled by `micro-usuarios` → returns JWT
- JWT stored in localStorage, injected by `apiFetch` wrapper for all API calls
- `onAuthChange` provides reactive session state to components
- OAuth2 callback landing page: `/acceder`

## Key File Locations

| What | Where |
|---|---|
| Frontend API client | `src/lib/api.ts` |
| Auth helpers | `src/lib/auth.ts` |
| Interactive map | `src/components/MapView.tsx` |
| Gateway JWT filter | `backend/orquestador/src/.../JwtGatewayFilter.java` |
| Matching algorithm | `backend/micro-coincidencias/app/services/scoring_service.py` |
| DB schema / migrations | `backend/micro-mascotas/src/main/resources/` |
| Env variables template | `backend/.env.example` |

## Environment Variables

Copy `backend/.env.example` to `backend/.env` before running Docker Compose. Required vars:
- `POSTGRES_*`, `MONGO_ROOT_*`, `MINIO_ROOT_*` — infra credentials
- `GOOGLE_CLIENT_ID`, `GOOGLE_CLIENT_SECRET` — OAuth2 (from Google Cloud Console)
- `JWT_SECRET`, `JWT_EXPIRATION_MS` — shared across all Java services
- `KAFKA_BOOTSTRAP_SERVERS` — `kafka:9092` inside Docker network

## Technology Stack

- **Frontend:** Astro v6, React 18, TypeScript, Tailwind CSS, Leaflet (maps)
- **Backend (Java):** Spring Boot 3.3 / Java 21, Spring Cloud Gateway (WebFlux), Spring Security, jjwt
- **Backend (Python):** FastAPI, sentence-transformers, CLIP (PyTorch CPU), rapidfuzz, pgvector
- **Infrastructure:** PostgreSQL 16 + pgvector, MongoDB 7, Kafka (Confluent), Debezium 2.6, MinIO
- **Build:** pnpm (frontend), Maven (Java), pip (Python), Docker multi-stage builds