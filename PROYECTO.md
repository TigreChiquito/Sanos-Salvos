# Sanos & Salvos — Referencia del proyecto

Plataforma comunitaria chilena para mascotas perdidas y encontradas en la Región Metropolitana.
Estado actual: **frontend completo, backend pendiente.**

---

## Stack

| Capa | Tecnología |
|---|---|
| Framework | Astro v4 |
| UI interactiva | React 18 (islands con `client:only="react"`) |
| Mapa | Leaflet 1.9 (importado dinámicamente en `useEffect` para evitar SSR) |
| Estilos | CSS puro (no Tailwind en uso real) + variables CSS globales |
| Auth | Mock en `localStorage` — **pendiente reemplazar por backend real** |
| Backend | **No implementado todavía** |

---

## Estructura de archivos

```
src/
├── components/
│   ├── Navbar.astro        # Navbar para páginas públicas (index, nosotros)
│   ├── NavbarApp.astro     # Navbar para páginas de app (mapa, reportar)
│   ├── MapView.tsx         # Componente React del mapa completo
│   ├── Hero.astro
│   ├── Features.astro
│   ├── FAQ.astro
│   └── Footer.astro
├── layouts/
│   └── BaseLayout.astro    # Layout base con fuentes (Fraunces + Nunito)
├── lib/
│   └── auth.ts             # Mock auth: getSession / login / logout / onAuthChange
├── pages/
│   ├── index.astro         # Landing page pública
│   ├── mapa.astro          # App: mapa de reportes
│   ├── reportar.astro      # App: formulario de nuevo reporte (requiere sesión)
│   ├── nosotros.astro      # Página pública "Sobre nosotros"
│   └── acceder.astro       # Login + registro en una sola página
└── styles/
    ├── global.css          # Variables CSS, reset, tipografía base
    └── mapa.css            # Estilos específicos del mapa
```

---

## Paleta de colores

```css
--terra:    #C45C3A   /* terracota — acción principal */
--terra-d:  #9E4026   /* terracota oscuro — hover */
--brown:    #2D1B12   /* marrón — texto principal sobre claro */
--cream:    #FDF8F0   /* fondo de panel / header */
--sand:     #F5EFE0   /* fondo de página */
```

Tipografías: **Fraunces** (serif, titulares) + **Nunito** (sans, cuerpo/UI).

---

## Auth mock (`src/lib/auth.ts`)

```ts
interface MockUser { name: string; email: string; initials: string; }

getSession()          // → MockUser | null  (lee localStorage)
login(user)           // guarda en localStorage + dispara evento 'ss:authchange'
logout()              // limpia localStorage + dispara evento 'ss:authchange'
onAuthChange(cb)      // suscribe callback, retorna función de limpieza
toInitials(name)      // "Carlos Méndez" → "CM"
```

Los navbars usan `data-auth="guest"` / `data-auth="user"` en los `<li>` para mostrar/ocultar según estado.

**Todo esto debe reemplazarse** cuando exista el backend real. La interfaz de `login()` y `getSession()` puede mantenerse, solo cambia la implementación interna (JWT en cookie/header en lugar de localStorage).

---

## Páginas y rutas

### `/` — Landing (público)
Navbar.astro + Hero + Features + FAQ + Footer. Sin auth.

### `/nosotros` — Sobre nosotros (público)
Navbar.astro. Hero oscuro con imagen, timeline de historia, grid de valores, CTA.

### `/acceder` — Login / Registro (público)
Tabs login/registro en una sola página. Al hacer submit llama a `login()` del mock.
**Pendiente:** conectar al endpoint real del backend (`POST /api/auth/login`, `POST /api/auth/register`).

### `/mapa` — Mapa de reportes (público, navbar de app)
NavbarApp con `active="mapa"`. Usa `<MapView client:only="react" />`.

**MapView.tsx** incluye:
- Panel lateral colapsable (flex puro, transición por `width`)
- Tarjeta de detalle animada desde abajo
- Filtros por tipo (perdido/encontrado) y raza
- Datos de mock hardcodeados — **pendiente conectar a `GET /api/reportes`**
- Bounds restringidos a Región Metropolitana: `[[-34.6, -71.8], [-32.9, -70.0]]`
- Tiles: Carto light (`https://{s}.basemaps.cartocdn.com/light_all/{z}/{x}/{y}{r}.png`)

### `/reportar` — Formulario de reporte (requiere sesión)
NavbarApp con `active="reportar"`. Redirige a `/acceder` si no hay sesión.

Campos del formulario:
1. **Tipo** — `perdido` | `encontrado` (radio pill)
2. **Animal** — `perro` | `gato` | `otro` (radio pill)
3. **Nombre** — texto, opcional
4. **Características** — raza (dropdown dinámico por animal), color, tamaño
5. **Descripción** — textarea 600 chars máx
6. **Fotos** — drag & drop, hasta 5, FileReader preview (aún no se suben a ningún lado)
7. **Ubicación** — Leaflet, clic para pin, coordenadas guardadas en `input[hidden]`

**Pendiente:** conectar submit a `POST /api/reportes` (multipart para fotos).

---

## Lo que el backend necesita proveer

### Entidades mínimas

**Usuario**
```
id, nombre, apellido, email, password_hash, created_at
```

**Reporte**
```
id, tipo (perdido|encontrado), animal (perro|gato|otro),
nombre, raza, color, tamano, descripcion,
lat, lng, zona (texto derivado de geocodificación inversa),
fotos[] (URLs), usuario_id, estado (activo|resuelto),
created_at, updated_at
```

### Endpoints mínimos

| Método | Ruta | Descripción |
|---|---|---|
| `POST` | `/api/auth/register` | Registro de usuario |
| `POST` | `/api/auth/login` | Login, retorna token/sesión |
| `POST` | `/api/auth/logout` | Cierra sesión |
| `GET` | `/api/auth/me` | Usuario actual (valida token) |
| `GET` | `/api/reportes` | Lista reportes (filtros: tipo, animal, bounds) |
| `POST` | `/api/reportes` | Crea reporte (multipart/form-data con fotos) |
| `GET` | `/api/reportes/:id` | Detalle de un reporte |
| `PATCH` | `/api/reportes/:id` | Actualizar estado (ej: marcar resuelto) |
| `DELETE` | `/api/reportes/:id` | Eliminar reporte (solo autor) |

### Almacenamiento de fotos
Opciones a definir: almacenamiento local en dev, S3/Cloudflare R2/Supabase Storage en producción.

### Autenticación
A definir: JWT en header `Authorization: Bearer`, o cookie httpOnly. El frontend actualmente simula la sesión en localStorage — adaptar `src/lib/auth.ts` para consumir el endpoint real.

---

## Puntos de integración en el frontend

| Archivo | Cambio necesario |
|---|---|
| `src/lib/auth.ts` | Reemplazar mock por llamadas a `/api/auth/*` |
| `src/pages/acceder.astro` | `login()` → fetch a `/api/auth/login` |
| `src/pages/reportar.astro` | Submit → fetch a `POST /api/reportes` |
| `src/components/MapView.tsx` | Datos hardcodeados → fetch a `GET /api/reportes` |

---

## Decisiones de diseño relevantes

- El **mapa siempre usa tiles claros** (Carto light). El panel lateral es cream/sand para armonizar con el resto del sitio.
- Las páginas públicas usan `Navbar.astro`; las de app usan `NavbarApp.astro` (fondo oscuro fijo, sin animación fadeDown).
- El componente MapView usa **flex puro** para panel + handle + mapa: sin `position: absolute` en el panel, sin z-index entre ellos. El colapso del panel es por `width: 0` (no `transform`), para que el mapa gane el espacio liberado.
- La **Detail card** del mapa es un sheet que sube desde abajo con `transform: translateY`. Se mantiene oscura intencionalmente como contraste de modal.
- Restricción de zona: el mapa no permite navegar fuera de la RM (`maxBounds` + `maxBoundsViscosity: 1.0`).
