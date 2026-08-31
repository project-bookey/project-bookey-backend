import Constants from 'expo-constants';
import { Platform } from 'react-native';

import { getTokens, setTokens, clearTokens } from '@/store/tokenStorage';

/**
 * API 베이스 URL 결정.
 * Expo Go 로 실제 기기에서 열면 localhost 가 폰 자신을 가리키므로,
 * 개발 서버 호스트(=맥의 LAN IP)를 그대로 사용한다.
 */
function resolveBaseUrl(): string {
  const configured = process.env.EXPO_PUBLIC_API_URL
    ?? (Constants.expoConfig?.extra as { apiBaseUrl?: string } | undefined)?.apiBaseUrl;

  if (process.env.EXPO_PUBLIC_API_URL) {
    return process.env.EXPO_PUBLIC_API_URL;
  }
  if (Platform.OS === 'web') {
    return configured ?? 'http://localhost:8080';
  }
  const hostUri = Constants.expoConfig?.hostUri ?? Constants.expoGoConfig?.debuggerHost;
  const host = hostUri?.split(':')[0];
  if (host && host !== 'localhost' && host !== '127.0.0.1') {
    return `http://${host}:8080`;
  }
  return configured ?? 'http://localhost:8080';
}

export const API_BASE_URL = resolveBaseUrl();

export class ApiError extends Error {
  constructor(
    readonly status: number,
    readonly code: string,
    message: string,
  ) {
    super(message);
  }
}

type RequestOptions = {
  method?: 'GET' | 'POST' | 'PATCH' | 'DELETE';
  body?: unknown;
  auth?: boolean;
  query?: Record<string, string | number | boolean | undefined>;
};

let refreshing: Promise<boolean> | null = null;

async function refreshAccessToken(): Promise<boolean> {
  const tokens = await getTokens();
  if (!tokens?.refreshToken) {
    return false;
  }
  const response = await fetch(`${API_BASE_URL}/api/v1/auth/refresh`, {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({ refreshToken: tokens.refreshToken }),
  });
  if (!response.ok) {
    await clearTokens();
    return false;
  }
  const data = await response.json();
  await setTokens({ accessToken: data.accessToken, refreshToken: data.refreshToken });
  return true;
}

export async function api<T>(path: string, options: RequestOptions = {}): Promise<T> {
  const { method = 'GET', body, auth = true, query } = options;

  const url = new URL(`${API_BASE_URL}${path}`);
  if (query) {
    Object.entries(query).forEach(([key, value]) => {
      if (value !== undefined && value !== null && value !== '') {
        url.searchParams.set(key, String(value));
      }
    });
  }

  const send = async (): Promise<Response> => {
    const headers: Record<string, string> = { 'Content-Type': 'application/json' };
    if (auth) {
      const tokens = await getTokens();
      if (tokens?.accessToken) {
        headers.Authorization = `Bearer ${tokens.accessToken}`;
      }
    }
    return fetch(url.toString(), {
      method,
      headers,
      body: body === undefined ? undefined : JSON.stringify(body),
    });
  };

  let response = await send();

  // 액세스 토큰 만료 시 1회만 갱신을 시도한다 (동시 요청은 하나의 갱신을 공유).
  if (response.status === 401 && auth) {
    refreshing = refreshing ?? refreshAccessToken();
    const refreshed = await refreshing;
    refreshing = null;
    if (refreshed) {
      response = await send();
    }
  }

  if (response.status === 204) {
    return undefined as T;
  }

  const text = await response.text();
  const data = text ? JSON.parse(text) : undefined;

  if (!response.ok) {
    throw new ApiError(
      response.status,
      data?.code ?? 'UNKNOWN',
      data?.message ?? '요청을 처리하지 못했습니다.',
    );
  }
  return data as T;
}
