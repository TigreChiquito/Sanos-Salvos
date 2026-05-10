# 🐾 Sanos & Salvos

Plataforma comunitaria para reportar y reunir mascotas perdidas en la Región Metropolitana de Santiago, Chile.

Los usuarios publican reportes con fotos y ubicación geográfica. Un motor de inteligencia artificial detecta automáticamente posibles coincidencias entre reportes de animales "perdidos" y "encontrados", comparando raza, color, tamaño, descripción textual e imágenes.

---

## Tecnologías

**Frontend:** Astro · React · TypeScript · Leaflet

**Backend:** Spring Boot 3 (Java 21) · FastAPI (Python 3.11) · Spring Cloud Gateway

**Infraestructura:** PostgreSQL + pgvector · MongoDB · Apache Kafka · Debezium · MinIO · Docker

---

## Requisitos previos

Asegúrate de tener instalado:

- [Docker Desktop](https://www.docker.com/products/docker-desktop/) (con Docker Compose v2)
- [Node.js](https://nodejs.org/) 20+ y [pnpm](https://pnpm.io/) (o npm)
- Una cuenta de Google Cloud con un proyecto OAuth2 configurado ([ver instrucciones](#google-cloud-console))

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
ss-micro-usuarios      | Started UsuariosApplication in X.X seconds
ss-micro-mascotas      | Started MascotasApplication in X.X seconds
ss-micro-coincidencias | Application startup complete.
ss-orquestador         | Started OrquestadorApplication in X.X seconds
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
npm install   # o: pnpm install
npm run dev   # o: pnpm dev
```

El frontend estará disponible en **http://localhost:4321**

---

## Google Cloud Console

Para que el login con Google funcione debes configurar un OAuth2 Client:

1. Ir a https://console.cloud.google.com → **APIs & Services → Credentials**
2. Crear un **OAuth 2.0 Client ID** de tipo "Web application"
3. En **Authorized redirect URIs**, agregar exactamente:
   ```
   http://localhost:8081/login/oauth2/code/google
   ```
4. Copiar el **Client ID** y **Client Secret** al archivo `backend/.env`
5. En **OAuth consent screen** → agregar tu email en "Test users" (mientras esté en modo Testing)

---

## URLs de acceso local

| Servicio | URL |
|---|---|
| **Frontend** | http://localhost:4321 |
| **API Gateway** | http://localhost:8080 |
| **Swagger UI** | http://localhost:8080/swagger-ui.html |
| **MinIO Console** (fotos) | http://localhost:9001 |
| **Health check** | http://localhost:8080/actuator/health |

---

## Arquitectura

```
┌─────────────────────────────────┐
│  Frontend  (Astro + React)      │
│  http://localhost:4321          │
└────────────┬────────────────────┘
             │ HTTPS · JWT Bearer
             ▼
┌─────────────────────────────────┐
│  Orquestador (API Gateway)      │  :8080
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
│  :8081  │  │  :8082   │  │ :8083               │
│ OAuth2  │  │ CQRS     │  │ sentence-transformers│
│ JWT     │  │ MinIO    │  │ CLIP · pgvector      │
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

## Comandos útiles

```bash
# Ver logs en tiempo real de un servicio
docker compose logs -f micro-mascotas

# Reiniciar solo un microservicio (sin recompilar)
docker compose restart micro-coincidencias

# Ver estado de todos los servicios
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
│   │   └── reportar.astro   # Publicar nuevo reporte
│   └── components/
│       ├── MapView.tsx      # Mapa Leaflet interactivo
│       └── NavbarApp.astro  # Navbar con sesión activa
└── backend/
    ├── .env.example         # Plantilla de variables
    ├── docker-compose.yml   # Toda la infraestructura
    ├── postgres/init/       # Schema SQL inicial
    ├── kafka/connectors/    # Configuración Debezium
    ├── micro-usuarios/      # Spring Boot :8081
    ├── micro-mascotas/      # Spring Boot :8082
    ├── micro-coincidencias/ # FastAPI :8083
    └── orquestador/         # Spring Cloud Gateway :8080
```

---

## Notas de desarrollo

**Primera ejecución lenta:** Los modelos de ML (sentence-transformers + CLIP) se descargan durante el `docker build` del contenedor `micro-coincidencias`. Tras el primer build quedan cacheados en la imagen Docker.

**Arranque secuencial:** Los microservicios esperan a que sus dependencias estén `healthy` antes de iniciar. El orden es: postgres/mongodb/kafka → debezium/micro-usuarios/micro-mascotas → micro-coincidencias → orquestador.

**Puerto 8083:** Tanto Debezium como micro-coincidencias exponen el puerto 8083. En el entorno Docker esto no genera conflicto porque se comunican por la red interna, pero al acceder desde el host puede ser ambiguo. Se recomienda mover micro-coincidencias al puerto 8084 en una próxima iteración.
