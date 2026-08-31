'use client';

import { useMutation } from '@tanstack/react-query';
import { useRouter } from 'next/navigation';
import { useState } from 'react';

import { AdminApiError, setToken } from '@/lib/api';
import { authApi } from '@/lib/endpoints';
import { Button, Card, Input } from '@/components/ui';

/** 관리자 로그인 — 서비스 계정과 분리된 이메일·비밀번호 + 2FA (§F13). */
export default function LoginPage() {
  const router = useRouter();
  const [email, setEmail] = useState('admin@bookey.local');
  const [password, setPassword] = useState('');
  const [totpCode, setTotpCode] = useState('');
  const [needsTotp, setNeedsTotp] = useState(false);
  const [error, setError] = useState<string | null>(null);

  const login = useMutation({
    mutationFn: () => authApi.login(email, password, totpCode),
    onSuccess: (result) => {
      if (result.totpRequired) {
        setNeedsTotp(true);
        setError(null);
        return;
      }
      if (result.accessToken) {
        setToken(result.accessToken);
        router.replace('/');
      }
    },
    onError: (e) =>
      setError(e instanceof AdminApiError ? e.message : '로그인하지 못했습니다.'),
  });

  return (
    <div className="flex min-h-screen items-center justify-center px-6">
      <div className="w-full max-w-sm">
        <div className="mb-8">
          <h1 className="font-serif text-[34px] font-bold tracking-tight">bookey</h1>
          <div className="mt-3 h-[3px] w-10 bg-[var(--color-ink)]" />
          <p className="eyebrow mt-3">ADMIN CONSOLE</p>
        </div>

        <Card className="p-6">
          <form
            className="flex flex-col gap-4"
            onSubmit={(event) => {
              event.preventDefault();
              login.mutate();
            }}
          >
            <Input
              label="이메일"
              type="email"
              value={email}
              onChange={(e) => setEmail(e.target.value)}
              autoComplete="username"
              required
            />
            <Input
              label="비밀번호"
              type="password"
              value={password}
              onChange={(e) => setPassword(e.target.value)}
              autoComplete="current-password"
              required
            />
            {needsTotp ? (
              <Input
                label="2단계 인증 코드"
                inputMode="numeric"
                maxLength={6}
                value={totpCode}
                onChange={(e) => setTotpCode(e.target.value.replace(/\D/g, ''))}
                hint="인증 앱에 표시된 6자리 숫자"
                autoFocus
              />
            ) : null}

            {error ? (
              <p className="font-mono text-[11.5px] text-[var(--color-danger)]">{error}</p>
            ) : null}

            <Button type="submit" disabled={login.isPending}>
              {login.isPending ? '확인 중…' : '로그인'}
            </Button>
          </form>
        </Card>

        <p className="mt-4 font-mono text-[11px] text-[var(--color-faint)]">
          모든 접속과 조회는 감사 로그에 기록됩니다.
        </p>
      </div>
    </div>
  );
}
