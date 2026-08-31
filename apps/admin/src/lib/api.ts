/**
 * 관리자 API 클라이언트.
 *
 * 서비스 API 와 완전히 분리된 /admin/v1/** 만 호출한다.
 * 토큰은 sessionStorage 에 둔다 — 탭을 닫으면 사라지고, 30분 유휴 만료는 서버가 강제한다(§F13).
 */
export const ADMIN_API_BASE =
  process.env.NEXT_PUBLIC_ADMIN_API_URL ?? 'http://localhost:8080';

const TOKEN_KEY = 'bookey.admin.token';

export function getToken(): string | null {
  if (typeof window === 'undefined') return null;
  return window.sessionStorage.getItem(TOKEN_KEY);
}

export function setToken(token: string) {
  window.sessionStorage.setItem(TOKEN_KEY, token);
}

export function clearToken() {
  window.sessionStorage.removeItem(TOKEN_KEY);
}

export class AdminApiError extends Error {
  constructor(
    readonly status: number,
    readonly code: string,
    message: string,
  ) {
    super(message);
  }
}

type Options = {
  method?: 'GET' | 'POST' | 'PATCH' | 'DELETE';
  body?: unknown;
  query?: Record<string, string | number | boolean | undefined | null>;
  auth?: boolean;
};

export async function adminApi<T>(path: string, options: Options = {}): Promise<T> {
  const { method = 'GET', body, query, auth = true } = options;

  const url = new URL(`${ADMIN_API_BASE}${path}`);
  if (query) {
    Object.entries(query).forEach(([key, value]) => {
      if (value !== undefined && value !== null && value !== '') {
        url.searchParams.set(key, String(value));
      }
    });
  }

  const headers: Record<string, string> = { 'Content-Type': 'application/json' };
  if (auth) {
    const token = getToken();
    if (token) headers.Authorization = `Bearer ${token}`;
  }

  const response = await fetch(url.toString(), {
    method,
    headers,
    body: body === undefined ? undefined : JSON.stringify(body),
  });

  if (response.status === 401 && auth) {
    clearToken();
    if (typeof window !== 'undefined' && !window.location.pathname.startsWith('/login')) {
      window.location.href = '/login';
    }
  }

  if (response.status === 204) return undefined as T;

  const text = await response.text();
  const data = text ? JSON.parse(text) : undefined;

  if (!response.ok) {
    throw new AdminApiError(
      response.status,
      data?.code ?? 'UNKNOWN',
      data?.message ?? '요청을 처리하지 못했습니다.',
    );
  }
  return data as T;
}
