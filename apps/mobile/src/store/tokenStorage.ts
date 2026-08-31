import AsyncStorage from '@react-native-async-storage/async-storage';
import * as SecureStore from 'expo-secure-store';
import { Platform } from 'react-native';

const KEY = 'bookey.tokens';

export type Tokens = { accessToken: string; refreshToken: string };

/** 네이티브는 SecureStore(키체인), 웹은 AsyncStorage 를 쓴다. */
const storage = {
  async get(key: string) {
    if (Platform.OS === 'web') {
      return AsyncStorage.getItem(key);
    }
    return SecureStore.getItemAsync(key);
  },
  async set(key: string, value: string) {
    if (Platform.OS === 'web') {
      return AsyncStorage.setItem(key, value);
    }
    return SecureStore.setItemAsync(key, value);
  },
  async remove(key: string) {
    if (Platform.OS === 'web') {
      return AsyncStorage.removeItem(key);
    }
    return SecureStore.deleteItemAsync(key);
  },
};

export async function getTokens(): Promise<Tokens | null> {
  const raw = await storage.get(KEY);
  return raw ? (JSON.parse(raw) as Tokens) : null;
}

export async function setTokens(tokens: Tokens) {
  await storage.set(KEY, JSON.stringify(tokens));
}

export async function clearTokens() {
  await storage.remove(KEY);
}
