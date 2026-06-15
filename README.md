# 🐾 Sanos & Salvos

Plataforma comunitaria para reportar y reunir mascotas perdidas en la Región Metropolitana de Santiago, Chile.

Los usuarios publican reportes con fotos y ubicación geográfica. Un motor de inteligencia artificial detecta automáticamente posibles coincidencias entre reportes de animales "perdidos" y "encontrados", comparando raza, color, tamaño, descripción textual e imágenes.

---

## Tecnologías

**Frontend:** Astro · React · TypeScript · Leaflet

**Backend:** Spring Boot 3 (Java 21) · FastAPI (Python 3.11) · Spring Cloud Gateway

**Infraestructura:** PostgreSQL + pgvector · MongoDB · Apache Kafka · Debezium · MinIO · NGINX · Docker

**Observabilidad:** Prometheus · Grafana

---

## Requisitos previos

Asegúrate de tener instalado:

- [Docker Desktop](https://www.docker.com/products/docker-desktop/) (con WSL2 o Hyper-V habilitado)
- [Node.js](https://nodejs.org/) 20+ y [pnpm](https://pnpm.io/)
- Una cuenta de Google Cloud con un proyecto OAuth2 configurado ([ver instrucciones](#google-cloud-console))

> **Primera vez con pnpm:** si al ejecutar `pnpm install` aparece el error `ERR_PNPM_IGNORED_BUILDS`, ejecuta `pnpm approve-builds`, selecciona `esbuild` y `sharp` con la barra espaciadora y confirma. Luego vuelve a correr `pnpm install`.

---

## Instalación y ejecución local

### 1. Clonar el repositorio

```bash
git clone <url-del-repo>
cd sanos-salvos
```

### 2. Configurar el backend

```bash
cd backend
cp .env.example .env
```

Edita `backend/.env` con tus credenciales. Los campos obligatorios son:

```env
# Genera un valor seguro: openssl rand -base64 64
JWT_SECRET=<string-aleatorio-largo>

# Obtener en Google Cloud Console (ver sección más abajo)
GOOGLE_CLIENT_ID=<tu-client-id>.apps.googleusercontent.com
GOOGLE_CLIENT_SECRET=<tu-client-secret>
```

Los demás campos tienen valores por defecto válidos para desarrollo local.

### 3. Configurar el frontend

En la raíz del proyecto crea el archivo `.env`:

```bash
# En la raíz (no dentro de /backend)
echo "PUBLIC_API_URL=http://localhost:8080" > .env
```

### 4. Levantar el backend con Docker

```bash
cd backend
docker compose up --build
```

Este comando construye y levanta todos los servicios: bases de datos, Kafka, Debezium, MinIO y los cuatro microservicios. El primer arranque puede tardar **10–15 minutos** porque:

- Maven descarga dependencias de los microservicios Java
- El Dockerfile de `micro-coincidencias` descarga los modelos de ML (~800 MB: sentence-transformers + CLIP)

Los servicios están listos cuando ves en los logs:

```
backend-micro-usuarios-1      | Started UsuariosApplication in X.X seconds
backend-micro-mascotas-1      | Started MascotasApplication in X.X seconds
backend-micro-coincidencias-1 | Application startup complete.
backend-orquestador-1         | Started OrquestadorApplication in X.X seconds
ss-nginx                      | ready
```

### 5. Registrar el conector Debezium (una sola vez)

Una vez que todos los servicios estén healthy, registra el conector CDC que sincroniza PostgreSQL → Kafka:

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

El frontend estará disponible en **http://localhost:4321**

---

## Google Cloud Console

Para que el login con Google funcione debes configurar un OAuth2 Client:

1. Ir a https://console.cloud.google.com → **APIs & Services → Credentials**
2. Crear un **OAuth 2.0 Client ID** de tipo "Web application"
3. En **Authorized redirect URIs**, agregar exactamente:
   ```
   http://localhost:8080/login/oauth2/code/google
   ```
4. Copiar el **Client ID** y **Client Secret** al archivo `backend/.env`
5. En **OAuth consent screen** → agregar tu email en "Test users" (mientras esté en modo Testing)

---

## URLs de acceso local

| Servicio | URL | Credenciales |
|---|---|---|
| **Frontend** | http://localhost:4321 | — |
| **API Gateway** | http://localhost:8080 | — |
| **Swagger UI** | http://localhost:8080/swagger-ui.html | — |
| **Grafana** (dashboards) | http://localhost:3000 | admin / admin |
| **Prometheus** | http://localhost:9090 | — |
| **MinIO Console** (fotos) | http://localhost:9001 | minioadmin / minioadmin |
| **Health check** | http://localhost:8080/actuator/health | — |

---

## Arquitectura

```
┌─────────────────────────────────┐
│  Frontend  (Astro + React)      │
│  http://localhost:4321          │
└────────────┬────────────────────┘
             │ HTTP · JWT Bearer
             ▼
┌─────────────────────────────────┐
│  NGINX  (Load Balancer)         │  :8080
│  · Docker DNS round-robin       │
│  · resolver 127.0.0.11          │
│  · timeout 35s (ML)             │
└────────────┬────────────────────┘
             │
             ▼
┌─────────────────────────────────┐
│  Orquestador (API Gateway)      │  (sin puerto de host, N réplicas)
│  Spring Cloud Gateway           │
│  · Valida JWT                   │
│  · CORS                         │
│  · Circuit Breaker              │
└────┬──────────┬─────────────────┘
     │          │
     ▼          ▼
┌─────────┐  ┌──────────┐  ┌─────────────────────┐
│ micro-  │  │ micro-   │  │ micro-coincidencias  │
│usuarios │  │mascotas  │  │ FastAPI + ML         │
│ OAuth2  │  │ CQRS     │  │ sentence-transformers│
│ JWT     │  │ MinIO    │  │ CLIP · pgvector      │
│(scalable│  │(scalable)│  │   (scalable)         │
└────┬────┘  └─────┬────┘  └──────────┬──────────┘
     │             │                   │
     └─────────────┴───────────────────┘
                   │ Kafka
     ┌─────────────┴───────────────────┐
     │                                 │
     ▼                                 ▼
┌──────────────┐              ┌─────────────────┐
│  PostgreSQL  │◄─ Debezium ─│     Kafka        │
│  (pgvector)  │              │  + Zookeeper     │
│  :5432       │              │  :9092 / :29092  │
└──────────────┘              └─────────────────┘
     ▲
     │ CQRS sync
     ▼
┌──────────────┐    ┌──────────┐
│   MongoDB    │    │  MinIO   │
│   :27017     │    │  :9000   │
│ (read model) │    │  (fotos) │
└──────────────┘    └──────────┘

Observabilidad (scrape cada 15s):
┌──────────────┐    ┌──────────┐
│  Prometheus  │───▶│ Grafana  │
│   :9090      │    │  :3000   │
│ /metrics de  │    │ dashboards│
│ cada micro   │    │ pre-conf. │
└──────────────┘    └──────────┘
```

### Motor de coincidencias

Cuando se publica un nuevo reporte, `micro-coincidencias` lo compara contra todos los reportes del tipo opuesto usando 7 dimensiones:

| Dimensión | Peso | Método |
|---|---|---|
| Nombre | 10% | Similitud fuzzy (rapidfuzz) |
| Raza | 20% | Similitud fuzzy |
| Color | 15% | Similitud fuzzy parcial |
| Tamaño | 10% | Match exacto |
| Descripción | 20% | Coseno entre embeddings (sentence-transformers) |
| Ubicación | 15% | Distancia Haversine (máx. 10 km) |
| Imagen | 10% | Coseno entre embeddings (CLIP) |

Si el score total supera **0.60**, se registra una coincidencia en la base de datos.

---

## Observabilidad (Prometheus + Grafana)

El stack incluye monitoreo completo de todos los microservicios. Prometheus recolecta métricas cada 15 segundos desde los endpoints `/actuator/prometheus` (Spring Boot) y `/metrics` (FastAPI), y Grafana las visualiza con dashboards pre-configurados que se cargan automáticamente al iniciar.

Accede a Grafana en **http://localhost:3000** con las credenciales `admin / admin`.

El dashboard principal **Sanos & Salvos — Overview** incluye:

- Tasa de requests por microservicio (req/s)
- Latencia de respuesta (p50, p95, p99)
- Estado de los Circuit Breakers (CLOSED / OPEN / HALF_OPEN)
- Uso de memoria y CPU por contenedor
- Eventos de Kafka (mensajes producidos y consumidos)
- Estado de la conexión a bases de datos (connection pool)

Para cambiar las credenciales de Grafana, agrega estas variables a `backend/.env`:

```env
GRAFANA_ADMIN_USER=tu-usuario
GRAFANA_ADMIN_PASSWORD=tu-password-seguro
```

---

## Comandos útiles

```bash
# Ver logs en tiempo real de un servicio (todas sus réplicas)
docker compose logs -f micro-mascotas

# Escalar un microservicio a N réplicas (NGINX distribuye automáticamente)
docker compose up --scale micro-mascotas=2 -d
docker compose up --scale micro-usuarios=2 --scale orquestador=2 -d

# Reiniciar solo un microservicio (sin recompilar)
docker compose restart micro-coincidencias

# Ver estado de todos los servicios y réplicas
docker compose ps

# Levantar solo la infraestructura (sin los microservicios)
docker compose up postgres mongodb kafka zookeeper debezium minio

# Herramientas de desarrollo (Kafka UI + mongo-express)
docker compose --profile dev up

# Detener y eliminar todo (incluyendo volúmenes de datos)
docker compose down -v
```

---

## Estructura del proyecto

```
sanos-salvos/
├── .env                     # Variables frontend (PUBLIC_API_URL)
├── astro.config.mjs
├── package.json
├── src/
│   ├── lib/
│   │   ├── api.ts           # Cliente HTTP: apiFetch() con JWT automático
│   │   └── auth.ts          # Auth: login, logout, getSession, onAuthChange
│   ├── pages/
│   │   ├── index.astro      # Landing page
│   │   ├── acceder.astro    # Login con Google
│   │   ├── mapa.astro       # Mapa de reportes
│   │   ├── reportar.astro   # Publicar nuevo reporte
│   │   └── perfil.astro     # Perfil de usuario (contacto + notificaciones)
│   └── components/
│       ├── MapView.tsx      # Mapa Leaflet interactivo
│       ├── Navbar.astro     # Navbar landing (con dropdown de usuario)
│       └── NavbarApp.astro  # Navbar app (con dropdown de usuario)
└── backend/
    ├── .env.example         # Plantilla de variables
    ├── docker-compose.yml   # Toda la infraestructura
    ├── nginx/
    │   └── nginx.conf       # Load balancer: round-robin al orquestador
    ├── postgres/init/       # Schema SQL inicial (incluye telefono, notif_*)
    ├── kafka/connectors/    # Configuración Debezium
    ├── monitoring/
    │   ├── prometheus/
    │   │   └── prometheus.yml       # Scrape config (usa nombres de servicio Docker)
    │   └── grafana/
    │       ├── provisioning/        # Datasource + dashboard providers
    │       └── dashboards/          # sanos-salvos-overview.json
    ├── micro-usuarios/      # Spring Boot — incluye PATCH /api/usuarios/me
    ├── micro-mascotas/      # Spring Boot :8082
    ├── micro-coincidencias/ # FastAPI :8084
    └── orquestador/         # Spring Cloud Gateway
```

---

## Notas de desarrollo

**Primera ejecución lenta:** Los modelos de ML (sentence-transformers + CLIP) se descargan durante el `docker build` del contenedor `micro-coincidencias`. Tras el primer build quedan cacheados en la imagen Docker.

**Arranque secuencial:** Los microservicios esperan a que sus dependencias estén `healthy` antes de iniciar. El orden es: postgres/mongodb/kafka → debezium/micro-usuarios/micro-mascotas → micro-coincidencias → orquestador → nginx.

**Balanceo de carga:** NGINX (`ss-nginx`) escucha en `localhost:8080` y distribuye el tráfico entre todas las réplicas del orquestador usando Docker DNS (`resolver 127.0.0.11`). Los microservicios no exponen puertos al host — se accede a ellos solo desde la red interna Docker. Para escalar: `docker compose up --scale micro-mascotas=2 -d`.

**Sin conflicto de puertos:** Al no tener puertos de host fijos, múltiples réplicas de un mismo servicio pueden correr sin colisionar. El único puerto de host expuesto por los microservicios de app es `8080` (NGINX).
