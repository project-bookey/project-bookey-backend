'use client';

import { useQuery } from '@tanstack/react-query';
import Link from 'next/link';

import { dashboardApi, moderationApi, opsApi } from '@/lib/endpoints';
import { PageHeader, Shell } from '@/components/Shell';
import { Card, Eyebrow, Empty, Tag, remainingSla } from '@/components/ui';

/** 대시보드 — KPI 카드 + 처리 대기 큐 요약 (§F13). */
export default function DashboardPage() {
  const dashboard = useQuery({ queryKey: ['dashboard'], queryFn: dashboardApi.get });
  const queue = useQuery({
    queryKey: ['moderation', 'PENDING'],
    queryFn: () => moderationApi.queue('PENDING'),
  });
  const stats = useQuery({ queryKey: ['ops', 'stats'], queryFn: opsApi.notificationStats });

  const data = dashboard.data;

  return (
    <Shell>
      <PageHeader title="대시보드" description="오늘의 지표와 처리해야 할 일" />

      <div className="px-7 py-6">
        <div className="grid grid-cols-2 gap-3 lg:grid-cols-4">
          <Stat label="전체 회원" value={data?.totalUsers ?? 0} />
          <Stat label="오늘 활동 회원" value={data?.activeUsersToday ?? 0} />
          <Stat label="오늘 독서 세션" value={data?.readingSessionsToday ?? 0} />
          <Stat label="오늘 완독" value={data?.finishedBooksToday ?? 0} />
          <Stat
            label="검증 리뷰 비율"
            value={`${Math.round((data?.verifiedReviewRatio ?? 0) * 100)}%`}
            target="목표 70%"
            warn={(data?.verifiedReviewRatio ?? 0) < 0.7}
          />
          <Stat
            label="알림 전환율 (7일)"
            value={`${Math.round((data?.notificationConversionRate7d ?? 0) * 100)}%`}
            target="목표 18%"
            warn={(data?.notificationConversionRate7d ?? 0) < 0.18}
          />
          <Stat label="활성 모임" value={data?.activeClubs ?? 0} />
          <Stat
            label="SLA 초과 신고"
            value={data?.overdueModeration ?? 0}
            warn={(data?.overdueModeration ?? 0) > 0}
          />
        </div>

        {(data?.overdueModeration ?? 0) > 0 ? (
          <div className="mt-5 rounded-xl border-l-4 border-[var(--color-danger)] bg-[var(--color-danger-soft)] px-5 py-4">
            <p className="font-mono text-[12px] font-bold text-[var(--color-danger)]">
              48시간 SLA를 넘긴 신고가 {data?.overdueModeration}건 있습니다
            </p>
            <p className="mt-1 text-[13px] text-[var(--color-muted)]">
              미처리 48시간 초과 비율이 10%를 넘으면 안티 지표에 걸립니다.
            </p>
          </div>
        ) : null}

        <section className="mt-8">
          <div className="mb-3 flex items-end justify-between">
            <Eyebrow>처리 대기 신고</Eyebrow>
            <Link href="/moderation" className="font-mono text-[12px] underline">
              전체 보기
            </Link>
          </div>

          <Card>
            {(queue.data?.content.length ?? 0) === 0 ? (
              <Empty>대기 중인 신고가 없습니다.</Empty>
            ) : (
              <ul className="divide-y divide-[var(--color-line)]">
                {queue.data!.content.slice(0, 6).map((ticket) => {
                  const sla = remainingSla(ticket.slaDueAt);
                  return (
                    <li key={ticket.id} className="flex items-center gap-4 px-5 py-3.5">
                      <Tag tone={ticket.priority <= 1 ? 'danger' : 'neutral'}>
                        {ticket.sourceType}
                      </Tag>
                      <div className="min-w-0 flex-1">
                        <p className="truncate text-[14px]">
                          {ticket.contentPreview ?? '(내용 없음)'}
                        </p>
                        <p className="mt-0.5 font-mono text-[11px] text-[var(--color-faint)]">
                          {ticket.authorNickname ?? '알 수 없음'} · 신고 {ticket.reportCount}건 ·{' '}
                          {ticket.reason}
                        </p>
                      </div>
                      <span
                        className={`numeral text-[11.5px] ${
                          sla.overdue ? 'text-[var(--color-danger)]' : 'text-[var(--color-muted)]'
                        }`}
                      >
                        {sla.label}
                      </span>
                    </li>
                  );
                })}
              </ul>
            )}
          </Card>
        </section>

        <section className="mt-8">
          <Eyebrow>알림 운영</Eyebrow>
          <Card className="mt-3 px-5 py-4">
            <div className="flex flex-wrap items-center gap-x-10 gap-y-3">
              <Metric label="7일 발송" value={stats.data?.sent7d ?? 0} />
              <Metric label="7일 전환" value={stats.data?.converted7d ?? 0} />
              <Metric
                label="전환율"
                value={`${Math.round((stats.data?.conversionRate ?? 0) * 100)}%`}
              />
              <div>
                <p className="eyebrow">푸시 스위치</p>
                <p className="mt-1">
                  {stats.data?.pushEnabled ? (
                    <Tag tone="accent">발송 중</Tag>
                  ) : (
                    <Tag tone="danger">중단됨</Tag>
                  )}
                </p>
              </div>
            </div>
          </Card>
        </section>
      </div>
    </Shell>
  );
}

function Stat({ label, value, target, warn }: {
  label: string;
  value: number | string;
  target?: string;
  warn?: boolean;
}) {
  return (
    <Card className="px-5 py-4">
      <p className="eyebrow">{label}</p>
      <p
        className={`numeral mt-2 text-[26px] leading-none ${
          warn ? 'text-[var(--color-danger)]' : ''
        }`}
      >
        {value}
      </p>
      {target ? (
        <p className="mt-1.5 font-mono text-[10.5px] text-[var(--color-faint)]">{target}</p>
      ) : null}
    </Card>
  );
}

function Metric({ label, value }: { label: string; value: number | string }) {
  return (
    <div>
      <p className="eyebrow">{label}</p>
      <p className="numeral mt-1 text-[18px]">{value}</p>
    </div>
  );
}
