# Sanos & Salvos

Plataforma comunitaria para reportar y reunir mascotas perdidas en la Región Metropolitana de Santiago, Chile.

Los usuarios publican reportes con fotos y ubicación geográfica. Un motor de inteligencia artificial detecta automáticamente posibles coincidencias entre reportes de animales "perdidos" y "encontrados", comparando raza, color, tamaño, descripción textual e imágenes mediante embeddings de texto (768D) e imagen CLIP (512D) independientes.

---

## Tecnologías

**Frontend:** Astro 6 · React 18 · TypeScript · Tailwind CSS · Leaflet

**Backend:** Spring Boot 3.3 (Java 21) · FastAPI (Python 3.11) · Spring Cloud Gateway (WebFlux)

**ML:** sentence-transformers `paraphrase-multilingual-mpnet-base-v2` (768D) · CLIP `ViT-B/32` (512D) · pgvector

**Infraestructura:** PostgreSQL 16 + pgvector · MongoDB 7 · Apache Kafka (Confluent 7.6) · Debezium 2.6 · MinIO · NGINX 1.27

**Observabilidad:** Prometheus 2.53 · Grafana 11.1

**Despliegue:** Docker Compose (desarrollo) · Kubernetes — Docker Desktop (producción local)

---

## Requisitos previos

- [Docker Desktop](https://www.docker.com/products/docker-desktop/) con WSL2 o Hyper-V habilitado
- [Node.js](https://nodejs.org/) 20+ y [pnpm](https://pnpm.io/) (`npm install -g pnpm`)
- [kubectl](https://kubernetes.io/docs/tasks/tools/) (solo para despliegue en Kubernetes)
- Una cuenta de Google Cloud con proyecto OAuth2 configurado ([ver instrucciones](#google-cloud-console))

> **Primera vez con pnpm:** si `pnpm install` devuelve `ERR_PNPM_IGNORED_BUILDS`, ejecuta `pnpm approve-builds`, selecciona `esbuild` y `sharp` con la barra espaciadora y confirma. Luego vuelve a correr `pnpm install`.

---

## Instalación y ejecución local

### 1. Clonar el repositorio

```bash
git clone <url-del-repo>
cd sanos-salvos
```

### 2. Configurar variables de entorno

```bash
cd backend
cp .env.example .env
```

Edita `backend/.env` con tus credenciales. Campos obligatorios:

```env
# Genera con: openssl rand -base64 64
JWT_SECRET=<string-aleatorio-largo>

# Desde Google Cloud Console (ver sección más abajo)
GOOGLE_CLIENT_ID=<tu-client-id>.apps.googleusercontent.com
GOOGLE_CLIENT_SECRET=<tu-client-secret>
```

Los demás campos tienen valores válidos para desarrollo local.

### 3. Configurar el frontend

```bash
# En la raíz del proyecto (no dentro de /backend)
echo "PUBLIC_API_URL=http://localhost:8080" > .env
```

### 4. Levantar el backend con Docker Compose

```bash
cd backend
docker compose up --build
```

Construye y levanta todos los servicios: bases de datos, Kafka, Debezium, MinIO, microservicios y NGINX. El primer arranque tarda **10–15 minutos** porque Maven descarga dependencias y el Dockerfile de `micro-coincidencias` descarga los modelos de ML (~800 MB).

Los servicios están listos cuando ves:

```
backend-micro-usuarios-1      | Started UsuariosApplication in X.X seconds
backend-micro-mascotas-1      | Started MascotasApplication in X.X seconds
backend-micro-coincidencias-1 | Application startup complete.
backend-orquestador-1         | Started OrquestadorApplication in X.X seconds
ss-nginx                      | start worker process
```

### 5. Registrar el conector Debezium (una sola vez)

Una vez que todos los servicios estén healthy, registra el conector CDC que captura cambios de PostgreSQL → Kafka:

```bash
curl -X POST http://localhost:8083/connectors \
  -H "Content-Type: application/json" \
  -d @kafka/connectors/debezium-connector.json
```

Verifica que quedó registrado:

```bash
curl http://localhost:8083/connectors
# Esperado: ["ss-postgres-connector"]
```

### 6. Levantar el frontend

Abre una nueva terminal en la raíz del proyecto:

```bash
pnpm install
pnpm dev
```

Frontend disponible en **http://localhost:4321**

---

## Despliegue en Kubernetes

Opción alternativa a Docker Compose, usando el cluster embebido de Docker Desktop.

### Prerrequisitos

1. Abre Docker Desktop → **Settings** → **Kubernetes** → activa **Enable Kubernetes** → **Apply & Restart**
2. Espera ~2 minutos hasta que el ícono de Kubernetes en la barra de estado quede en verde
3. Verifica: `kubectl get nodes` debe mostrar `docker-desktop` en estado `Ready`

### Construir las imágenes locales

Las imágenes se construyen desde Docker Compose y luego se etiquetan con los nombres que espera Kubernetes:

```bash
# Construir desde /backend
cd backend
docker compose build

# Etiquetar para Kubernetes
docker tag backend-micro-usuarios:latest    ss-micro-usuarios:latest
docker tag backend-micro-mascotas:latest    ss-micro-mascotas:latest
docker tag backend-micro-coincidencias:latest ss-micro-coincidencias:latest
docker tag backend-orquestador:latest       ss-orquestador:latest
```

### Desplegar con el script automático (recomendado)

Desde la raíz del proyecto, en PowerShell:

```powershell
.\k8s\deploy.ps1
```

El script despliega todo en orden: namespace → secrets → configmaps → bases de datos → Kafka → MinIO → Debezium → microservicios → NGINX, esperando a que cada capa esté `Ready` antes de continuar. Al finalizar registra el conector Debezium automáticamente.

Para eliminar todo el despliegue:

```powershell
.\k8s\deploy.ps1 -Down
```

### Desplegar manualmente (paso a paso)

```bash
# 1. Namespace, secrets y configuración
kubectl apply -f k8s/00-namespace.yaml
kubectl apply -f k8s/01-secrets.yaml
kubectl apply -f k8s/02-configmap.yaml
kubectl apply -f k8s/03-postgres-init.yaml
kubectl apply -f k8s/04-nginx-config.yaml

# 2. Infraestructura
kubectl apply -f k8s/infra/

# 3. Microservicios y NGINX (una vez la infra esté Ready)
kubectl apply -f k8s/microservices/
kubectl apply -f k8s/nginx/

# 4. Verificar estado de pods
kubectl get pods -n sanos-salvos

# 5. Registrar conector Debezium (una sola vez)
.\k8s\debezium-connector.ps1
```

### Migración de base de datos (solo si ya tenías datos)

Si la base de datos ya tenía datos de una versión anterior, aplica la migración de embeddings:

```bash
kubectl exec -n sanos-salvos statefulset/postgres -- \
  psql -U ss_admin -d sanos_salvos -c \
  "ALTER TABLE reportes ADD COLUMN IF NOT EXISTS embedding_texto vector(768);
   ALTER TABLE reportes ADD COLUMN IF NOT EXISTS embedding_imagen vector(512);"
```

### Rebuild y redeploy de un microservicio

Cuando cambias código, reconstruye la imagen y reinicia el deployment:

```bash
# Reconstruir (desde /backend)
docker compose build micro-coincidencias
docker tag backend-micro-coincidencias:latest ss-micro-coincidencias:latest

# Redeploy (Kubernetes toma la nueva imagen)
kubectl rollout restart deployment/micro-coincidencias -n sanos-salvos
kubectl rollout status deployment/micro-coincidencias -n sanos-salvos
```

### Escalar servicios en Kubernetes

```bash
# Escalar microservicio a 2 réplicas
kubectl scale deployment micro-mascotas --replicas=2 -n sanos-salvos

# Ver estado de todos los pods
kubectl get pods -n sanos-salvos

# Ver logs de un deployment
kubectl logs -n sanos-salvos deployment/micro-coincidencias -f

# Eliminar namespace completo
kubectl delete namespace sanos-salvos
```

---

## Google Cloud Console

Para que el login con Google funcione:

1. Ir a https://console.cloud.google.com → **APIs & Services → Credentials**
2. Crear un **OAuth 2.0 Client ID** de tipo "Web application"
3. En **Authorized redirect URIs**, agregar:
   ```
   http://localhost:8080/login/oauth2/code/google
   ```
4. Copiar el **Client ID** y **Client Secret** al archivo `backend/.env`
5. En **OAuth consent screen** → agregar tu email en "Test users" (mientras esté en modo Testing)

---

## URLs de acceso local

### Docker Compose

| Servicio | URL | Credenciales |
|---|---|---|
| **Frontend** | http://localhost:4321 | — |
| **API Gateway** | http://localhost:8080 | — |
| **Swagger UI** | http://localhost:8080/swagger-ui.html | — |
| **Grafana** | http://localhost:3000 | admin / admin |
| **Prometheus** | http://localhost:9090 | — |
| **MinIO Console** | http://localhost:9001 | ss_minio_admin / (ver .env) |
| **MinIO API** | http://localhost:9000 | — |
| **Debezium REST** | http://localhost:8083 | — |
| **Health check** | http://localhost:8080/actuator/health | — |
| **Kafka UI** *(dev)* | http://localhost:8080 | — |
| **mongo-express** *(dev)* | http://localhost:8081 | — |

> Las herramientas de desarrollo (Kafka UI, mongo-express) solo arrancan con `docker compose --profile dev up`.

### Kubernetes (Docker Desktop)

| Servicio | URL |
|---|---|
| **API Gateway (NGINX)** | http://localhost:30080 |
| **MinIO Console** | http://localhost:30901 |
| **MinIO API** | http://localhost:30900 |

---

## Arquitectura

```
┌─────────────────────────────────────┐
│  Frontend (Astro + React)           │
│  http://localhost:4321              │
└──────────────┬──────────────────────┘
               │ HTTP · JWT Bearer
               ▼
┌─────────────────────────────────────┐
│  NGINX (Load Balancer)              │  :8080 (Compose) / :30080 (K8s)
│  · Docker/K8s DNS round-robin       │
│  · Proxy al orquestador             │
│  · Sin estado (escalable)           │
└──────────────┬──────────────────────┘
               │
               ▼
┌─────────────────────────────────────┐
│  Orquestador (Spring Cloud Gateway) │  sin puerto de host, N réplicas
│  · Valida JWT (JwtGatewayFilter)    │
│  · CORS                             │
│  · Circuit Breaker (Resilience4j)   │
│  · Inyecta X-User-Id / X-User-Email │
└────┬──────────┬────────────┬────────┘
     │          │            │
     ▼          ▼            ▼
┌─────────┐ ┌──────────┐ ┌──────────────────────┐
│micro-   │ │micro-    │ │micro-coincidencias    │
│usuarios │ │mascotas  │ │FastAPI · ML           │
│OAuth2   │ │CQRS      │ │sentence-transformers  │
│JWT      │ │MinIO     │ │CLIP · pgvector        │
│:8081    │ │:8082     │ │:8084                  │
└────┬────┘ └─────┬────┘ └──────────┬────────────┘
     │            │                 │
     └────────────┴─────────────────┘
                  │ Kafka Events
     ┌────────────┴─────────────────────┐
     │                                  │
     ▼                                  ▼
┌──────────────┐               ┌────────────────┐
│  PostgreSQL  │◄── Debezium ──│     Kafka       │
│  + pgvector  │     (CDC)     │  + Zookeeper    │
│  :5432       │               │  :29092 (host)  │
└──────┬───────┘               └────────────────┘
       │ CQRS sync
       ▼
┌──────────────┐   ┌──────────┐
│   MongoDB    │   │  MinIO   │
│   :27017     │   │  :9000   │
│ (read model) │   │  (fotos) │
└──────────────┘   └──────────┘

Observabilidad (scrape cada 15s):
┌──────────────┐   ┌──────────────┐
│  Prometheus  │──▶│   Grafana    │
│  :9090       │   │  :3000       │
│  /metrics de │   │  Dashboard:  │
│  cada micro  │   │  ss-overview │
└──────────────┘   └──────────────┘
```

### Flujo de autenticación

```
Usuario → /oauth2/authorization/google
       → micro-usuarios (Spring Security OAuth2)
       → Google OAuth2 callback
       → OAuth2SuccessHandler genera JWT (jjwt 0.12.5)
       → Redirect a FRONTEND_URL/acceder?token=<JWT>
       → Frontend almacena JWT en localStorage (ss_token)
       → apiFetch() inyecta Authorization: Bearer <JWT>
       → JwtGatewayFilter valida JWT e inyecta:
           X-User-Id, X-User-Email, X-User-Name
```

### Flujo de reporte → matching

```
POST /api/reportes (multipart/form-data + fotos)
  → micro-mascotas
  → Escribe en PostgreSQL + sube fotos a MinIO
  → Debezium detecta cambio en PostgreSQL
  → Publica en Kafka topic: ss.reportes.created / .updated
  → micro-coincidencias consume el evento
  → Genera embedding_texto (768D) y embedding_imagen (512D)
  → Compara contra candidatos del tipo opuesto
  → Si score_total ≥ 0.60 → coincidencia en PostgreSQL
  → Publica en Kafka: ss.coincidencias.found
  → ReporteSyncConsumer actualiza MongoDB (read model del mapa)
```

---

## Motor de coincidencias

Cuando se publica un reporte, `micro-coincidencias` lo compara contra todos los reportes del tipo opuesto usando 7 dimensiones independientes:

| Dimensión | Peso | Método |
|---|---|---|
| Nombre | 10% | Fuzzy match — `rapidfuzz.token_sort_ratio` |
| Raza | 20% | Fuzzy match — `rapidfuzz.token_sort_ratio` |
| Color | 15% | Fuzzy parcial — `rapidfuzz.partial_ratio` |
| Tamaño | 10% | Match exacto binario |
| Descripción | 20% | Coseno entre embeddings de **texto** (768D) |
| Ubicación | 15% | Distancia Haversine inversa (máx. 10 km) |
| Imagen | 10% | Coseno entre embeddings **CLIP** (512D) |

Si un campo es `None`, su peso se redistribuye proporcionalmente entre las dimensiones disponibles.
Si `score_total ≥ 0.60` (configurable con `MATCH_THRESHOLD`), se registra la coincidencia.

### Embeddings

El servicio genera y almacena **dos embeddings separados** por reporte:

- `embedding_texto vector(768)` — texto del reporte (nombre + raza + color + descripción) procesado por `paraphrase-multilingual-mpnet-base-v2`. Dimensión nativa, sin truncado.
- `embedding_imagen vector(512)` — primera foto del reporte procesada por CLIP `ViT-B/32`. Si la foto no está disponible, se omite (el peso de imagen se redistribuye).

Los modelos se descargan **durante el build de la imagen Docker** para evitar lentitud en el primer arranque.

---

## Kafka topics

| Topic | Productor | Consumidor | Descripción |
|---|---|---|---|
| `ss.reportes.created` | Debezium (CDC) | micro-coincidencias | Nuevo reporte en PostgreSQL |
| `ss.reportes.updated` | Debezium (CDC) | micro-coincidencias | Reporte actualizado |
| `ss.coincidencias.found` | micro-coincidencias | (pendiente: notificaciones) | Match detectado con score ≥ 0.60 |
| `ss.public.reportes` | Debezium | micro-mascotas | Sync CQRS → MongoDB |
| `ss.public.usuarios` | Debezium | — | CDC de usuarios |

---

## Rutas de la API

Todas las rutas pasan por el Gateway en `:8080` (Compose) o `:30080` (K8s).

| Método | Ruta | Auth | Microservicio |
|---|---|---|---|
| GET | `/oauth2/authorization/google` | No | micro-usuarios |
| GET | `/login/oauth2/code/google` | No | micro-usuarios |
| GET | `/api/auth/me` | JWT | micro-usuarios |
| PATCH | `/api/usuarios/me` | JWT | micro-usuarios (teléfono, notificaciones) |
| GET | `/api/reportes` | No | micro-mascotas (MongoDB) |
| POST | `/api/reportes` | JWT | micro-mascotas |
| GET | `/api/reportes/{id}` | No | micro-mascotas |
| PATCH | `/api/reportes/{id}/estado` | JWT | micro-mascotas |
| DELETE | `/api/reportes/{id}` | JWT | micro-mascotas |
| GET | `/api/coincidencias/{reporteId}` | JWT | micro-coincidencias |
| GET | `/actuator/health` | No | orquestador |
| GET | `/swagger-ui.html` | No | orquestador |

---

## Observabilidad

Prometheus recolecta métricas cada 15 segundos desde `/actuator/prometheus` (Spring Boot) y `/metrics` (FastAPI). Grafana las visualiza en el dashboard **Sanos & Salvos — Overview** (pre-cargado automáticamente), que incluye:

- Tasa de requests por microservicio (req/s)
- Latencia de respuesta (p50, p95, p99)
- Estado de los Circuit Breakers (CLOSED / OPEN / HALF_OPEN)
- Uso de memoria y CPU por contenedor
- Eventos de Kafka (mensajes producidos y consumidos)
- Scores de coincidencias (histograma de distribución)

Accede en **http://localhost:3000** con `admin / admin`.

Para cambiar las credenciales de Grafana:

```env
# En backend/.env
GRAFANA_ADMIN_USER=tu-usuario
GRAFANA_ADMIN_PASSWORD=tu-password
```

---

## Comandos útiles

### Docker Compose

```bash
# Levantar todo (primera vez o con cambios de código)
cd backend && docker compose up --build

# Levantar sin recompilar
docker compose up -d

# Solo infraestructura (sin microservicios)
docker compose up postgres mongodb kafka zookeeper debezium minio -d

# Herramientas de desarrollo (Kafka UI + mongo-express)
docker compose --profile dev up -d

# Escalar un microservicio (NGINX distribuye automáticamente)
docker compose up --scale micro-mascotas=2 -d
docker compose up --scale micro-usuarios=2 --scale orquestador=2 -d

# Ver logs en tiempo real de un servicio (todas sus réplicas)
docker compose logs -f micro-coincidencias

# Reiniciar solo un microservicio sin recompilar
docker compose restart micro-coincidencias

# Ver estado de todos los servicios
docker compose ps

# Detener y eliminar todo, incluyendo volúmenes
docker compose down -v
```

### Kubernetes

```bash
# Deploy completo (script automático)
.\k8s\deploy.ps1

# Eliminar todo el despliegue
.\k8s\deploy.ps1 -Down

# Ver todos los pods
kubectl get pods -n sanos-salvos

# Ver logs de un deployment
kubectl logs -n sanos-salvos deployment/micro-mascotas -f

# Reiniciar un deployment (toma nueva imagen si fue retagueada)
kubectl rollout restart deployment/micro-coincidencias -n sanos-salvos

# Escalar un microservicio
kubectl scale deployment micro-mascotas --replicas=2 -n sanos-salvos

# Registrar conector Debezium manualmente
.\k8s\debezium-connector.ps1

# Abrir una shell en el pod de PostgreSQL
kubectl exec -it -n sanos-salvos statefulset/postgres -- psql -U ss_admin -d sanos_salvos
```

---

## Estructura del proyecto

```
sanos-salvos/
├── .env                        # Variables frontend (PUBLIC_API_URL)
├── astro.config.mjs
├── package.json
├── docs/                       # Informes de pruebas (HTML + PDF)
├── k8s/                        # Manifiestos Kubernetes
│   ├── deploy.ps1              # Script de deploy automatizado
│   ├── debezium-connector.ps1  # Registro del conector CDC
│   ├── 00-namespace.yaml       # Namespace sanos-salvos
│   ├── 01-secrets.yaml         # Credenciales (DB, MinIO, Google, JWT)
│   ├── 02-configmap.yaml       # Configuración de servicios
│   ├── 03-postgres-init.yaml   # Schema SQL inicial (ConfigMap)
│   ├── 04-nginx-config.yaml    # Configuración NGINX
│   ├── infra/                  # Infra: postgres, mongo, zookeeper, kafka, minio, debezium
│   ├── microservices/          # Deployments: usuarios, mascotas, coincidencias, orquestador
│   └── nginx/                  # Deployment + Service NodePort NGINX
├── src/
│   ├── lib/
│   │   ├── api.ts              # Cliente HTTP: apiFetch() con JWT automático
│   │   └── auth.ts             # Auth: login, logout, getSession, onAuthChange
│   ├── pages/
│   │   ├── index.astro         # Landing page
│   │   ├── acceder.astro       # Login con Google (OAuth2 callback)
│   │   ├── mapa.astro          # Mapa de reportes (Leaflet)
│   │   ├── reportar.astro      # Publicar nuevo reporte
│   │   ├── perfil.astro        # Perfil: contacto + preferencias de notificación
│   │   └── nosotros.astro      # Sobre el proyecto
│   └── components/
│       ├── MapView.tsx         # Mapa Leaflet interactivo con marcadores
│       ├── Navbar.astro        # Navbar landing (con dropdown de usuario)
│       └── NavbarApp.astro     # Navbar app (con dropdown de usuario)
└── backend/
    ├── .env.example            # Plantilla de variables (copiar a .env)
    ├── docker-compose.yml      # Toda la infraestructura + microservicios
    ├── nginx/
    │   └── nginx.conf          # Load balancer con re-resolución DNS cada 5s
    ├── postgres/init/
    │   └── 01-schema.sql       # Schema completo con pgvector y CDC publication
    ├── kafka/connectors/
    │   └── debezium-connector.json
    ├── monitoring/
    │   ├── prometheus/
    │   │   └── prometheus.yml          # Scrape config (nombres de servicio Docker)
    │   └── grafana/
    │       ├── provisioning/           # Datasource + dashboard provider
    │       └── dashboards/
    │           └── sanos-salvos-overview.json
    ├── micro-usuarios/         # Spring Boot :8081 — OAuth2, JWT, perfiles
    ├── micro-mascotas/         # Spring Boot :8082 — reportes, CQRS, MinIO
    ├── micro-coincidencias/    # FastAPI :8084 — ML matching, embeddings, pgvector
    │   └── tests/              # Pruebas unitarias (pytest) — scoring y geo
    └── orquestador/            # Spring Cloud Gateway — JWT filter, circuit breakers
```

---

## Notas de desarrollo

**Primera ejecución lenta:** Los modelos de ML (`paraphrase-multilingual-mpnet-base-v2` + CLIP `ViT-B/32`) se descargan durante el `docker build` del contenedor `micro-coincidencias`. Tras el primer build quedan cacheados en la imagen Docker.

**Arranque secuencial:** Los microservicios esperan a que sus dependencias estén `healthy` antes de iniciar. El orden es: postgres / mongodb / kafka → debezium / micro-usuarios / micro-mascotas → micro-coincidencias → orquestador → nginx.

**Balanceo de carga:** NGINX escucha en `:8080` (Compose) o `:30080` (K8s) y distribuye el tráfico entre todas las réplicas del orquestador usando Docker/Kubernetes DNS. Para escalar en Docker Compose: `docker compose up --scale orquestador=2 -d`. Para escalar en K8s: `kubectl scale deployment orquestador --replicas=2 -n sanos-salvos`.

**Sin conflicto de puertos:** Los microservicios no exponen puertos al host (solo internos a la red Docker/K8s). El único puerto de host de los microservicios de app es `:8080` (Docker Compose) / `:30080` (K8s) a través de NGINX.

**Imágenes en Kubernetes:** Las imágenes se construyen con Docker Compose (prefijo `backend-`) y necesitan ser retagueadas como `ss-*` para que los manifiestos de K8s las encuentren. Los deployments usan `imagePullPolicy: Never` para usar imágenes locales sin registry externo.

**Embeddings:** El motor de coincidencias almacena dos vectores por reporte: `embedding_texto vector(768)` (texto nativo sin truncar) y `embedding_imagen vector(512)` (CLIP). Si la imagen no está disponible, el score de imagen es `null` y su peso (10%) se redistribuye automáticamente entre las otras dimensiones.

**Circuit Breakers:** El orquestador implementa circuit breakers con Resilience4j. Se abren al 50% de fallos en ventana de 10 llamadas, esperan 15 segundos en estado OPEN y necesitan 3 llamadas exitosas para volver a CLOSED. Timeouts: usuarios 5s, mascotas 15s (uploads), coincidencias 30s (inferencia ML).
