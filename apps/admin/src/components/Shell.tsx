'use client';

import { useQuery } from '@tanstack/react-query';
import Link from 'next/link';
import { usePathname, useRouter } from 'next/navigation';
import { useEffect } from 'react';

import { clearToken, getToken } from '@/lib/api';
import { authApi } from '@/lib/endpoints';

const NAV = [
  { href: '/', label: '대시보드' },
  { href: '/moderation', label: '신고 큐' },
  { href: '/users', label: '회원' },
  { href: '/books', label: '도서' },
  { href: '/reviews', label: '검증 심사' },
  { href: '/clubs', label: '모임' },
  { href: '/notifications', label: '알림 운영' },
  { href: '/audit', label: '감사 로그' },
];

/** 관리자 공통 셸. 로그인하지 않았으면 /login 으로 보낸다. */
export function Shell({ children }: { children: React.ReactNode }) {
  const router = useRouter();
  const pathname = usePathname();

  const me = useQuery({ queryKey: ['admin', 'me'], queryFn: authApi.me, retry: false });

  useEffect(() => {
    if (!getToken()) {
      router.replace('/login');
    }
  }, [router]);

  return (
    <div className="flex min-h-screen">
      <aside className="w-52 shrink-0 border-r border-[var(--color-line)] bg-[var(--color-surface)]">
        <div className="border-b border-[var(--color-line)] px-5 py-5">
          <p className="font-serif text-[19px] font-bold tracking-tight">bookey</p>
          <p className="eyebrow mt-1">ADMIN</p>
        </div>

        <nav className="p-2">
          {NAV.map((item) => {
            const active = item.href === '/' ? pathname === '/' : pathname.startsWith(item.href);
            return (
              <Link
                key={item.href}
                href={item.href}
                className={`block rounded-lg px-3 py-2 font-mono text-[12.5px] font-bold transition ${
                  active
                    ? 'bg-[var(--color-ink)] text-white'
                    : 'text-[var(--color-muted)] hover:bg-[var(--color-surface-alt)]'
                }`}
              >
                {item.label}
              </Link>
            );
          })}
        </nav>

        <div className="mt-auto border-t border-[var(--color-line)] px-5 py-4">
          <p className="text-[13px] font-bold">{me.data?.name ?? '—'}</p>
          <p className="font-mono text-[11px] text-[var(--color-faint)]">{me.data?.role ?? ''}</p>
          <button
            onClick={() => {
              clearToken();
              router.replace('/login');
            }}
            className="mt-2 font-mono text-[11px] text-[var(--color-muted)] underline"
          >
            로그아웃
          </button>
        </div>
      </aside>

      <main className="min-w-0 flex-1">{children}</main>
    </div>
  );
}

export function PageHeader({ title, description, action }: {
  title: string;
  description?: string;
  action?: React.ReactNode;
}) {
  return (
    <header className="flex items-end justify-between gap-4 border-b border-[var(--color-line)] px-7 py-6">
      <div>
        <h1 className="font-serif text-[24px] font-bold tracking-tight">{title}</h1>
        {description ? (
          <p className="mt-1 text-[13.5px] text-[var(--color-muted)]">{description}</p>
        ) : null}
      </div>
      {action}
    </header>
  );
}
