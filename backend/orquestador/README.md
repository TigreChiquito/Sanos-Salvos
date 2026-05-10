# orquestador

API Gateway — Spring Cloud Gateway + Resilience4j Circuit Breaker.

Único punto de entrada para el frontend. Valida JWT, aplica CORS, enruta hacia los microservicios y protege el sistema con Circuit Breaker.

---

## Tabla de rutas

| Ruta | Destino | Auth | Circuit Breaker |
|---|---|---|---|
| `/oauth2/**` | micro-usuarios:8081 | No | usuariosCircuitBreaker |
| `/login/oauth2/**` | micro-usuarios:8081 | No | usuariosCircuitBreaker |
| `/api/auth/**` | micro-usuarios:8081 | No* | usuariosCircuitBreaker |
| `/api/usuarios/**` | micro-usuarios:8081 | JWT | usuariosCircuitBreaker |
| `GET /api/reportes` | micro-mascotas:8082 | No | mascotasCircuitBreaker |
| `/api/reportes/**` | micro-mascotas:8082 | JWT | mascotasCircuitBreaker |
| `/api/coincidencias/**` | micro-coincidencias:8083 | JWT | coincidenciasCircuitBreaker |

*`/api/auth/me` y `/api/auth/logout` sí requieren JWT; el flujo OAuth2 no.

---

## Circuit Breaker — Resilience4j

```
CLOSED → operación normal
  ↓ (≥50% de fallos en ventana de 10 llamadas)
OPEN → falla rápida, retorna /fallback/{servicio} (503)
  ↓ (tras 15s)
HALF_OPEN → permite 3 llamadas de prueba
  ↓ (si pasan) → CLOSED
  ↓ (si fallan) → OPEN
```

Timeouts configurados:
- micro-usuarios: 5s
- micro-mascotas: 15s (uploads de fotos)
- micro-coincidencias: 30s (inferencia ML)

---

## Headers que el gateway añade a cada request interna

| Header | Contenido |
|---|---|
| `X-User-Id` | UUID del usuario extraído del JWT |
| `X-User-Email` | Email del usuario |
| `X-User-Name` | Nombre completo |

Los microservicios pueden leer estos headers directamente sin volver a validar el JWT.

---

## Endpoints de estado

| Ruta | Descripción |
|---|---|
| `/actuator/health` | Estado general del gateway |
| `/actuator/circuitbreakers` | Estado de cada circuit breaker |
| `/actuator/metrics` | Métricas de requests |
| `/swagger-ui.html` | Docs agregados de todos los micros |

---

## Variables de entorno

| Variable | Descripción |
|---|---|
| `JWT_SECRET` | Mismo secret que usan los microservicios |
| `FRONTEND_URL` | URL del frontend para CORS (default: http://localhost:4321) |
