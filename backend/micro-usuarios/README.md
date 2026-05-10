# micro-usuarios

Microservicio de Gestión de Usuarios — Spring Boot 3.3 + Java 21.

---

## ⚠️ Configuración requerida antes de levantar

### Google Cloud Console

Para que el flujo OAuth2 con Google funcione, debes registrar la URI de redirección autorizada en tu proyecto de Google Cloud:

1. Ir a [https://console.cloud.google.com/apis/credentials](https://console.cloud.google.com/apis/credentials)
2. Seleccionar (o crear) tu OAuth 2.0 Client ID
3. En **"Authorized redirect URIs"**, agregar:

```
http://localhost:8081/login/oauth2/code/google
```

> En producción, reemplazar `localhost:8081` por el dominio real del orquestador.

Sin este paso, Google rechazará el callback y el login fallará con `redirect_uri_mismatch`.

---

## Variables de entorno requeridas

Copiar `../.env.example` como `../.env` y completar:

| Variable | Descripción |
|---|---|
| `GOOGLE_CLIENT_ID` | Client ID de Google Cloud Console |
| `GOOGLE_CLIENT_SECRET` | Client Secret de Google Cloud Console |
| `JWT_SECRET` | String aleatorio ≥ 32 chars (usar `openssl rand -base64 64`) |
| `POSTGRES_*` | Credenciales de PostgreSQL |

---

## Endpoints

| Método | Ruta | Auth | Descripción |
|---|---|---|---|
| `GET` | `/oauth2/authorize/google` | No | Inicia el flujo OAuth2 con Google |
| `GET` | `/api/auth/me` | JWT | Perfil del usuario autenticado |
| `POST` | `/api/auth/logout` | JWT | Cierra sesión (invalida token en frontend) |
| `GET` | `/api/usuarios/{id}` | JWT | Perfil público de un usuario |

Swagger UI disponible en: [http://localhost:8081/swagger-ui.html](http://localhost:8081/swagger-ui.html)
