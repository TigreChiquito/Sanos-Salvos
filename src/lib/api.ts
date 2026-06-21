// src/lib/api.ts
// Cliente central de API para comunicarse con el orquestador (Spring Cloud Gateway).
// Todas las llamadas al backend deben pasar por apiFetch() para que el JWT se
// inyecte automáticamente en cada request.

export const API_URL =
  (import.meta.env.PUBLIC_API_URL as string | undefined) ?? 'http://localhost:8080';

const TOKEN_KEY = 'ss_token';

/** Lee el JWT almacenado en localStorage. */
function getStoredToken(): string | null {
  if (typeof localStorage === 'undefined') return null;
  return localStorage.getItem(TOKEN_KEY);
}

export interface ApiError {
  status: number;
  message: string;
}

/**
 * Wrapper de fetch que:
 *  - Antepone API_URL a la ruta
 *  - Inyecta el header Authorization: Bearer <token> si hay sesión activa
 *  - Lanza un ApiError tipado si la respuesta no es 2xx
 */
export async function apiFetch<T = unknown>(
  path: string,
  options: RequestInit = {},
): Promise<T> {
  const token = getStoredToken();

  const headers = new Headers(options.headers);

  if (token) {
    headers.set('Authorization', `Bearer ${token}`);
  }

  // No sobreescribir Content-Type si el body es FormData (el browser lo pone con boundary)
  if (!(options.body instanceof FormData) && !headers.has('Content-Type')) {
    headers.set('Content-Type', 'application/json');
  }

  const res = await fetch(`${API_URL}${path}`, {
    ...options,
    headers,
  });

  if (!res.ok) {
    let message = `HTTP ${res.status}`;
    try {
      const body = await res.json();
      message = body?.mensaje ?? body?.message ?? body?.error ?? message;
    } catch {
      // response no es JSON — usar status text
      message = res.statusText || message;
    }
    const err: ApiError = { status: res.status, message };
    throw err;
  }

  // 204 No Content u otras respuestas sin body
  const contentType = res.headers.get('Content-Type') ?? '';
  if (res.status === 204 || !contentType.includes('application/json')) {
    return undefined as T;
  }

  return res.json() as Promise<T>;
}
