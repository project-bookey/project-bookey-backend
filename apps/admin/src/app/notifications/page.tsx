'use client';

import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { useState } from 'react';

import { opsApi } from '@/lib/endpoints';
import { PageHeader, Shell } from '@/components/Shell';
import { Button, Card, Eyebrow, Tag, formatDateTime } from '@/components/ui';

const FLAG_LABEL: Record<string, { title: string; description: string }> = {
  PUSH_ENABLED: {
    title: '전체 푸시 발송',
    description: '끄면 예약된 알림이 발송되지 않습니다. 오발송 사고 시 긴급 차단용입니다.',
  },
  CLUB_CREATION_OPEN: {
    title: '모임 생성 허용',
    description: '끄면 새 모임을 만들 수 없습니다. 기존 모임은 그대로 운영됩니다.',
  },
  SIGNUP_OPEN: {
    title: '신규 가입 허용',
    description: '끄면 신규 가입이 막힙니다. 기존 회원 로그인은 유지됩니다.',
  },
};

/** 알림 운영 — 발송 통계와 긴급 킬스위치 (§F13). */
export default function NotificationsPage() {
  const queryClient = useQueryClient();
  const stats = useQuery({ queryKey: ['ops', 'stats'], queryFn: opsApi.notificationStats });
  const flags = useQuery({ queryKey: ['ops', 'flags'], queryFn: opsApi.flags });
  const [pending, setPending] = useState<string | null>(null);

  const toggle = useMutation({
    mutationFn: ({ key, enabled }: { key: string; enabled: boolean }) =>
      opsApi.updateFlag(key, enabled, enabled ? '운영자 재개' : '운영자 중단'),
    onSettled: () => {
      setPending(null);
      queryClient.invalidateQueries({ queryKey: ['ops'] });
    },
  });

  return (
    <Shell>
      <PageHeader title="알림 운영" description="발송 성과를 보고, 필요하면 즉시 멈춥니다." />

      <div className="px-7 py-6">
        <div className="grid grid-cols-3 gap-3">
          <Card className="px-5 py-4">
            <p className="eyebrow">7일 발송</p>
            <p className="numeral mt-2 text-[26px]">{stats.data?.sent7d ?? 0}</p>
          </Card>
          <Card className="px-5 py-4">
            <p className="eyebrow">7일 전환</p>
            <p className="numeral mt-2 text-[26px]">{stats.data?.converted7d ?? 0}</p>
          </Card>
          <Card className="px-5 py-4">
            <p className="eyebrow">전환율</p>
            <p
              className={`numeral mt-2 text-[26px] ${
                (stats.data?.conversionRate ?? 0) < 0.18 ? 'text-[var(--color-warn)]' : ''
              }`}
            >
              {Math.round((stats.data?.conversionRate ?? 0) * 100)}%
            </p>
            <p className="mt-1 font-mono text-[10.5px] text-[var(--color-faint)]">목표 18%</p>
          </Card>
        </div>

        <section className="mt-8">
          <Eyebrow>운영 스위치</Eyebrow>
          <Card className="mt-3">
            <ul className="divide-y divide-[var(--color-line)]">
              {(flags.data ?? []).map((flag) => {
                const meta = FLAG_LABEL[flag.key] ?? {
                  title: flag.key,
                  description: flag.note ?? '',
                };
                return (
                  <li key={flag.key} className="flex items-center gap-5 px-5 py-4">
                    <div className="flex-1">
                      <div className="flex items-center gap-2">
                        <p className="text-[14px] font-bold">{meta.title}</p>
                        {flag.enabled ? <Tag tone="accent">켜짐</Tag> : <Tag tone="danger">꺼짐</Tag>}
                      </div>
                      <p className="mt-1 text-[13px] text-[var(--color-muted)]">
                        {meta.description}
                      </p>
                      <p className="mt-1 font-mono text-[10.5px] text-[var(--color-faint)]">
                        최근 변경 {formatDateTime(flag.updatedAt)}
                      </p>
                    </div>
                    <Button
                      variant={flag.enabled ? 'danger' : 'primary'}
                      disabled={pending === flag.key}
                      onClick={() => {
                        setPending(flag.key);
                        toggle.mutate({ key: flag.key, enabled: !flag.enabled });
                      }}
                    >
                      {flag.enabled ? '중단' : '재개'}
                    </Button>
                  </li>
                );
              })}
            </ul>
          </Card>
        </section>
      </div>
    </Shell>
  );
}
