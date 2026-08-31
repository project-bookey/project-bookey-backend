import { create } from 'zustand';

import { authApi } from '@/api/endpoints';
import type { Me } from '@/api/types';
import { clearTokens, getTokens, setTokens } from './tokenStorage';

type AuthState = {
  user: Me | null;
  status: 'loading' | 'authenticated' | 'anonymous';
  restore: () => Promise<void>;
  devLogin: (handle: string, nickname?: string) => Promise<void>;
  logout: () => Promise<void>;
  setUser: (user: Me) => void;
};

export const useAuth = create<AuthState>((set) => ({
  user: null,
  status: 'loading',

  restore: async () => {
    const tokens = await getTokens();
    if (!tokens) {
      set({ status: 'anonymous', user: null });
      return;
    }
    try {
      const user = await authApi.me();
      set({ user, status: 'authenticated' });
    } catch {
      await clearTokens();
      set({ status: 'anonymous', user: null });
    }
  },

  devLogin: async (handle, nickname) => {
    const result = await authApi.devLogin(handle, nickname);
    await setTokens({ accessToken: result.accessToken, refreshToken: result.refreshToken });
    set({ user: result.user, status: 'authenticated' });
  },

  logout: async () => {
    try {
      await authApi.logout();
    } catch {
      // 서버 실패와 무관하게 로컬 토큰은 지운다.
    }
    await clearTokens();
    set({ user: null, status: 'anonymous' });
  },

  setUser: (user) => set({ user }),
}));
