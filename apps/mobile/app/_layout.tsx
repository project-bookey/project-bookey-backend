import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { Stack } from 'expo-router';
import { StatusBar } from 'expo-status-bar';
import { useEffect, useState } from 'react';
import { SafeAreaProvider } from 'react-native-safe-area-context';

import { Loading } from '@/components/ui';
import { useAuth } from '@/store/auth';
import { colors } from '@/theme';

const queryClient = new QueryClient({
  defaultOptions: {
    queries: { retry: 1, staleTime: 15_000, refetchOnWindowFocus: false },
  },
});

export default function RootLayout() {
  const restore = useAuth((s) => s.restore);
  const status = useAuth((s) => s.status);
  const [ready, setReady] = useState(false);

  useEffect(() => {
    restore().finally(() => setReady(true));
  }, [restore]);

  if (!ready || status === 'loading') {
    return <Loading />;
  }

  return (
    <QueryClientProvider client={queryClient}>
      <SafeAreaProvider>
        <StatusBar style="dark" />
        <Stack
          screenOptions={{
            headerStyle: { backgroundColor: colors.bg },
            headerShadowVisible: false,
            headerTintColor: colors.text,
            headerTitleStyle: { fontWeight: '700' },
            contentStyle: { backgroundColor: colors.bg },
          }}
        >
          <Stack.Screen name="(tabs)" options={{ headerShown: false }} />
          <Stack.Screen name="login" options={{ headerShown: false }} />
          <Stack.Screen name="search" options={{ title: '도서 검색' }} />
          <Stack.Screen name="timer" options={{ title: '독서 타이머', presentation: 'modal' }} />
          <Stack.Screen name="club/join" options={{ title: '코드로 참가' }} />
          <Stack.Screen name="club/create" options={{ title: '모임 만들기' }} />
          <Stack.Screen name="club/[id]/index" options={{ title: '모임' }} />
          <Stack.Screen name="club/[id]/posts" options={{ title: '토론' }} />
          <Stack.Screen name="club/[id]/result" options={{ title: '모임 결산' }} />
          <Stack.Screen name="book/[id]" options={{ title: '도서' }} />
        </Stack>
      </SafeAreaProvider>
    </QueryClientProvider>
  );
}
