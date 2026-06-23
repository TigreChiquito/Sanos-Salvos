# Guía para levantar Sanos & Salvos

Pasos completos para arrancar el backend y el frontend sin errores, desde cero o tras limpiar imágenes Docker.

---

## 0. Requisitos previos

| Herramienta | Versión mínima | Verificar |
|---|---|---|
| Docker Desktop | 4.x | `docker --version` |
| Node.js | 18+ | `node --version` |
| pnpm | 8+ | `pnpm --version` |
| Java (opcional, dev local) | 21 | `java --version` |

---

## 1. Preparar variables de entorno

```bash
# Desde la raíz del repo
cp backend/.env.example backend/.env
```

Edita `backend/.env` y completa:

```env
POSTGRES_DB=sanosysalvos
POSTGRES_USER=ssuser
POSTGRES_PASSWORD=<contraseña>

MONGO_ROOT_USER=mongoroot
MONGO_ROOT_PASSWORD=<contraseña>
MONGO_DB=sanosysalvos

MINIO_ROOT_USER=minioadmin
MINIO_ROOT_PASSWORD=<contraseña>
MINIO_BUCKET_FOTOS=fotos-mascotas

GOOGLE_CLIENT_ID=<desde Google Cloud Console>
GOOGLE_CLIENT_SECRET=<desde Google Cloud Console>

JWT_SECRET=<string aleatorio, mínimo 64 chars>
JWT_EXPIRATION_MS=86400000

KAFKA_BOOTSTRAP_SERVERS=kafka:9092
KAFKA_GROUP_ID=ss-consumers

GRAFANA_ADMIN_USER=admin
GRAFANA_ADMIN_PASSWORD=<contraseña>
```

> **Importante:** el archivo `.env` debe estar en `backend/`, no en la raíz del repo.

---

## 2. Limpiar estado anterior (solo si es arranque en frío)

Si hay contenedores o volúmenes viejos que puedan interferir:

```bash
# Desde backend/
docker compose down -v       # para todos los contenedores y borra volúmenes
docker system prune -f       # elimina capas huérfanas
```

Para eliminar también todas las imágenes (rebuild completo):

```bash
docker rmi -f $(docker images -q)
# Si hay contenedores en ejecución bloqueando:
docker rm -f $(docker ps -aq)
docker rmi -f $(docker images -q)
```

> **Nota:** Docker Desktop con Kubernetes habilitado mantiene imágenes de sistema
> (`registry.k8s.io/*`, `docker/desktop-*`) que no se pueden eliminar mientras
> Kubernetes esté activo. Es normal; no afectan al proyecto.

---

## 3. Levantar el backend

```bash
cd backend/
docker compose up -d --build
```

`--build` reconstruye las imágenes de los microservicios Java/Python.  
Omítelo en arranques normales (sin cambios de código) para ahorrar tiempo.

### Orden de arranque interno (gestionado por `depends_on` + healthchecks)

```
1. postgres      ←─ healthcheck pg_isready (~20 s)
   mongodb       ←─ healthcheck mongosh ping (~20 s)
2. zookeeper     ←─ healthcheck cub zk-ready (~15 s)
3. kafka         ←─ healthcheck broker-api-versions (~30 s)
4. debezium      ←─ espera kafka + postgres (~45 s arranque)
   minio         ←─ healthcheck mc ready (~10 s)
5. micro-usuarios      ←─ espera postgres + kafka (~60 s Spring Boot)
   micro-mascotas      ←─ espera postgres + mongo + kafka + minio (~60 s)
   micro-coincidencias ←─ espera postgres + kafka (~90 s, carga modelos ML)
6. orquestador         ←─ espera los 3 micros (~45 s Spring Gateway)
7. nginx               ←─ espera orquestador (inmediato)
8. prometheus + grafana ←─ arrancan al final
```

El arranque completo desde cero tarda entre **4 y 8 minutos** la primera vez
(descarga de imágenes + compilación Maven + carga de modelos ML).

### Verificar estado

```bash
docker compose ps              # todos deben estar "healthy" o "running"
docker compose logs -f         # logs en tiempo real de todos los servicios
docker compose logs -f micro-coincidencias   # solo el servicio ML (más lento)
```

---

## 4. Problemas frecuentes y soluciones

### Kafka o Zookeeper quedan en "starting" indefinidamente

```bash
docker compose restart zookeeper
docker compose restart kafka
```

Causa: el healthcheck `cub zk-ready` a veces falla en la primera verificación.

### micro-coincidencias nunca llega a "healthy"

El servicio carga dos modelos ML pesados al arrancar:
- `paraphrase-multilingual-mpnet-base-v2` (sentence-transformers)
- CLIP `ViT-B/32` (PyTorch)

La primera vez descarga los modelos (~800 MB). Espera al menos **5 minutos** antes
de diagnosticar un fallo real.

```bash
docker compose logs -f micro-coincidencias
# Busca: "Application startup complete" o "Uvicorn running on http://0.0.0.0:8084"
```

### orquestador no arranca (falla el healthcheck)

Depende de que los 3 microservicios estén healthy. Si alguno tarda, el orquestador
espera. Verifica cuál microservicio está bloqueando:

```bash
docker compose ps | grep -v "healthy"
```

### Puerto 8080 ocupado

NGINX ocupa el puerto `8080`. Asegúrate de no tener otro proceso usándolo:

```bash
# Windows
netstat -ano | findstr :8080
# Linux/Mac
lsof -i :8080
```

### Error "port is already allocated" en postgres (5432) o mongodb (27017)

Un contenedor anterior sigue corriendo. Ejecuta `docker compose down` antes de
volver a levantar.

### Debezium no registra el conector de PostgreSQL

Debezium arranca, pero no crea el conector automáticamente. Hay que registrarlo
manualmente una vez que esté healthy:

```bash
curl -X POST http://localhost:8083/connectors \
  -H "Content-Type: application/json" \
  -d @backend/kafka/debezium-connector.json
```

---

## 5. Levantar el frontend

```bash
# Desde la raíz del repo
pnpm install      # solo la primera vez o tras cambios en package.json
pnpm dev          # servidor en http://localhost:4321
```

El frontend apunta al backend en `http://localhost:8080` (NGINX).
Asegúrate de que el backend esté completamente levantado antes de usar el frontend.

---

## 6. URLs de los servicios

| Servicio | URL | Notas |
|---|---|---|
| Frontend | http://localhost:4321 | Astro dev server |
| API (entrada) | http://localhost:8080 | NGINX → orquestador |
| MinIO (consola) | http://localhost:9001 | Gestión de fotos |
| MinIO (API S3) | http://localhost:9000 | Para los micros internamente |
| Prometheus | http://localhost:9090 | Métricas raw |
| Grafana | http://localhost:3000 | Dashboards (admin / ver .env) |
| Debezium REST | http://localhost:8083 | Gestión de conectores CDC |
| PostgreSQL | localhost:5432 | Cliente: DBeaver, pgAdmin |
| MongoDB | localhost:27017 | Cliente: Compass |
| Kafka | localhost:29092 | Cliente: Offset Explorer |

### Herramientas dev (perfil opcional)

```bash
docker compose --profile dev up -d
```

| Herramienta | URL |
|---|---|
| Kafka UI | http://localhost:8090 |
| mongo-express | http://localhost:8081 |

---

## 7. Apagar

```bash
# Solo detener (conserva volúmenes y datos)
docker compose down

# Detener y borrar todos los datos (reset completo)
docker compose down -v
```

---

## 8. Escalar microservicios

```bash
docker compose up --scale micro-mascotas=2 -d
docker compose up --scale micro-usuarios=2 -d
docker compose up --scale micro-coincidencias=2 -d
docker compose up --scale orquestador=2 -d
```

NGINX resuelve el DNS de Docker (`resolver 127.0.0.11`) en cada request, distribuyendo
el tráfico automáticamente entre réplicas sin configuración adicional.
