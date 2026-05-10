// src/lib/auth.ts
// Autenticación basada en JWT real.
// El flujo es:
//   1. Usuario hace click en "Continuar con Google" → redirect a /oauth2/authorization/google
//   2. El gateway proxya el request a micro-usuarios (Spring Security OAuth2)
//   3. Google autentica al usuario → micro-usuarios genera JWT
//   4. micro-usuarios redirige a FRONTEND_URL/acceder?token=<JWT>
//   5. acceder.astro lee el param, llama login(token), redirige a /mapa

import { apiFetch, API_URL } from './api';

// ── Keys de localStorage ──────────────────────────────────────────────────────
const TOKEN_KEY = 'ss_token';
const USER_KEY  = 'ss_user';
const AUTH_EVENT = 'ss:authchange';

// ── Tipos ─────────────────────────────────────────────────────────────────────

export interface User {
  id: string;
  nombre: string;
  apellido: string;
  /** Nombre completo: "${nombre} ${apellido}". Incluido para compatibilidad con componentes existentes. */
  name: string;
  email: string;
  fotoPerfilUrl: string | null;
  initials: string;
}

/** Respuesta que devuelve GET /api/auth/me */
interface MeResponse {
  id: string;
  nombre: string;
  apellido: string;
  email: string;
  fotoPerfilUrl: string | null;
}

// ── API ───────────────────────────────────────────────────────────────────────

/**
 * Lee el JWT del localStorage.
 * Usado por apiFetch() para inyectarlo en los headers — también es útil para
 * comprobar rápidamente si hay sesión sin hacer un request.
 */
export function getToken(): string | null {
  if (typeof localStorage === 'undefined') return null;
  return localStorage.getItem(TOKEN_KEY);
}

/**
 * Devuelve el usuario cacheado desde localStorage.
 * No hace ningún request de red — usa la última respuesta de /api/auth/me.
 * Para validar el token contra el servidor usa refreshSession().
 */
export function getSession(): User | null {
  if (typeof localStorage === 'undefined') return null;
  try {
    const raw = localStorage.getItem(USER_KEY);
    return raw ? (JSON.parse(raw) as User) : null;
  } catch {
    return null;
  }
}

/**
 * Guarda el JWT, obtiene el perfil del usuario desde /api/auth/me
 * y cachea el resultado en localStorage.
 *
 * Lanza un error si el token es inválido o el servidor no responde.
 */
export async function login(token: string): Promise<User> {
  if (typeof localStorage === 'undefined') throw new Error('localStorage no disponible');

  // 1. Guardar token para que apiFetch lo pueda usar
  localStorage.setItem(TOKEN_KEY, token);

  try {
    // 2. Obtener datos del usuario desde el backend
    const me = await apiFetch<MeResponse>('/api/auth/me');
    const fullName = `${me.nombre} ${me.apellido}`.trim();
    const user: User = {
      id:            me.id,
      nombre:        me.nombre,
      apellido:      me.apellido,
      name:          fullName,
      email:         me.email,
      fotoPerfilUrl: me.fotoPerfilUrl,
      initials:      toInitials(fullName),
    };

    // 3. Cachear usuario
    localStorage.setItem(USER_KEY, JSON.stringify(user));

    // 4. Notificar a todos los listeners
    window.dispatchEvent(new CustomEvent(AUTH_EVENT, { detail: user }));

    return user;
  } catch (err) {
    // Si el token no es válido, limpiamos todo
    localStorage.removeItem(TOKEN_KEY);
    localStorage.removeItem(USER_KEY);
    throw err;
  }
}

/**
 * Cierra sesión: llama a POST /api/auth/logout en el backend (invalida la
 * sesión OAuth2 de Spring Security) y limpia localStorage.
 */
export async function logout(): Promise<void> {
  if (typeof localStorage === 'undefined') return;

  try {
    // Intentar invalidar sesión en el servidor (best-effort)
    await apiFetch('/api/auth/logout', { method: 'POST' });
  } catch {
    // Si el servidor no responde o el token ya expiró, continuamos igual
  } finally {
    localStorage.removeItem(TOKEN_KEY);
    localStorage.removeItem(USER_KEY);
    window.dispatchEvent(new CustomEvent(AUTH_EVENT, { detail: null }));
  }
}

/**
 * Hace un request de validación al servidor y actualiza el cache local.
 * Útil para refrescar los datos del perfil o verificar si el JWT sigue vigente.
 * Retorna null si el token expiró o el servidor no está disponible.
 */
export async function refreshSession(): Promise<User | null> {
  const token = getToken();
  if (!token) return null;

  try {
    const me = await apiFetch<MeResponse>('/api/auth/me');
    const fullName = `${me.nombre} ${me.apellido}`.trim();
    const user: User = {
      id:            me.id,
      nombre:        me.nombre,
      apellido:      me.apellido,
      name:          fullName,
      email:         me.email,
      fotoPerfilUrl: me.fotoPerfilUrl,
      initials:      toInitials(fullName),
    };
    localStorage.setItem(USER_KEY, JSON.stringify(user));
    window.dispatchEvent(new CustomEvent(AUTH_EVENT, { detail: user }));
    return user;
  } catch {
    // Token expirado o inválido → limpiar
    localStorage.removeItem(TOKEN_KEY);
    localStorage.removeItem(USER_KEY);
    window.dispatchEvent(new CustomEvent(AUTH_EVENT, { detail: null }));
    return null;
  }
}

/** Suscribe un callback a los cambios de sesión. Retorna la función de limpieza. */
export function onAuthChange(cb: (user: User | null) => void): () => void {
  const handler = (e: Event) => cb((e as CustomEvent<User | null>).detail);
  window.addEventListener(AUTH_EVENT, handler);
  return () => window.removeEventListener(AUTH_EVENT, handler);
}

/** Deriva iniciales desde un nombre completo. "Carlos Méndez" → "CM" */
export function toInitials(name: string): string {
  return name
    .trim()
    .split(/\s+/)
    .slice(0, 2)
    .map(w => w[0]?.toUpperCase() ?? '')
    .join('');
}

/**
 * URL del endpoint de autorización OAuth2 de Google, a través del gateway.
 * El frontend redirige aquí cuando el usuario hace click en "Continuar con Google".
 */
export const GOOGLE_AUTH_URL = `${API_URL}/oauth2/authorization/google`;
