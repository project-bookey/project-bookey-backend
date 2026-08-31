'use client';

import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { useState } from 'react';

import { usersApi } from '@/lib/endpoints';
import type { SanctionType, UserRow, UserStatus } from '@/lib/types';
import { PageHeader, Shell } from '@/components/Shell';
import {
  Button, Card, Empty, Input, Select, Table, Tag, formatDateTime, formatDuration,
} from '@/components/ui';

const STATUS_TONE: Record<UserStatus, 'neutral' | 'warn' | 'danger'> = {
  ACTIVE: 'neutral',
  WRITE_BANNED: 'warn',
  SUSPENDED: 'danger',
  TERMINATED: 'danger',
};

/** 회원 관리 — 조회는 마스킹이 기본, 전체 열람은 사유를 남겨야 한다 (§F13). */
export default function UsersPage() {
  const [keyword, setKeyword] = useState('');
  const [query, setQuery] = useState('');
  const [status, setStatus] = useState<UserStatus | ''>('');
  const [selected, setSelected] = useState<UserRow | null>(null);

  const users = useQuery({
    queryKey: ['users', query, status],
    queryFn: () => usersApi.list(query || undefined, status || undefined),
  });

  return (
    <Shell>
      <PageHeader title="회원" description="닉네임 · 핸들 · 이메일로 검색합니다." />

      <div className="px-7 py-6">
        <form
          className="mb-4 flex items-end gap-3"
          onSubmit={(e) => {
            e.preventDefault();
            setQuery(keyword.trim());
          }}
        >
          <div className="w-72">
            <Input
              label="검색"
              value={keyword}
              onChange={(e) => setKeyword(e.target.value)}
              placeholder="닉네임 · 핸들 · 이메일"
            />
          </div>
          <div className="w-40">
            <Select
              label="상태"
              value={status}
              onChange={(e) => setStatus(e.target.value as UserStatus | '')}
            >
              <option value="">전체</option>
              <option value="ACTIVE">정상</option>
              <option value="WRITE_BANNED">쓰기 정지</option>
              <option value="SUSPENDED">이용 정지</option>
              <option value="TERMINATED">영구 정지</option>
            </Select>
          </div>
          <Button type="submit">검색</Button>
        </form>

        <Card>
          {(users.data?.content.length ?? 0) === 0 ? (
            <Empty>조건에 맞는 회원이 없습니다.</Empty>
          ) : (
            <Table head={['회원', '이메일', '상태', '읽는 중', '완독', '가입', '']}>
              {users.data!.content.map((user) => (
                <tr key={user.id} className="border-b border-[var(--color-line)] last:border-0">
                  <td className="px-4 py-3">
                    <p className="text-[14px] font-bold">{user.nickname}</p>
                    <p className="font-mono text-[11px] text-[var(--color-faint)]">@{user.handle}</p>
                  </td>
                  <td className="px-4 py-3 font-mono text-[12px] text-[var(--color-muted)]">
                    {user.maskedEmail ?? '—'}
                  </td>
                  <td className="px-4 py-3">
                    <Tag tone={STATUS_TONE[user.status]}>{user.status}</Tag>
                  </td>
                  <td className="numeral px-4 py-3 text-[12px]">{user.booksReading}</td>
                  <td className="numeral px-4 py-3 text-[12px]">{user.booksFinished}</td>
                  <td className="px-4 py-3 font-mono text-[11px] text-[var(--color-faint)]">
                    {formatDateTime(user.createdAt)}
                  </td>
                  <td className="px-4 py-3 text-right">
                    <Button variant="outline" onClick={() => setSelected(user)}>
                      상세
                    </Button>
                  </td>
                </tr>
              ))}
            </Table>
          )}
        </Card>
      </div>

      {selected ? <UserDialog user={selected} onClose={() => setSelected(null)} /> : null}
    </Shell>
  );
}

function UserDialog({ user, onClose }: { user: UserRow; onClose: () => void }) {
  const queryClient = useQueryClient();
  const [revealReason, setRevealReason] = useState('');
  const [appliedReason, setAppliedReason] = useState<string | undefined>(undefined);
  const [sanctionType, setSanctionType] = useState<SanctionType>('WARN');
  const [sanctionReason, setSanctionReason] = useState('');
  const [durationDays, setDurationDays] = useState('7');
  const [error, setError] = useState<string | null>(null);

  const detail = useQuery({
    queryKey: ['user', user.id, appliedReason],
    queryFn: () => usersApi.detail(user.id, appliedReason),
  });

  const sanction = useMutation({
    mutationFn: () =>
      usersApi.sanction(user.id, {
        type: sanctionType,
        reason: sanctionReason.trim(),
        durationDays: durationDays ? Number(durationDays) : undefined,
      }),
    onSuccess: () => {
      setSanctionReason('');
      queryClient.invalidateQueries({ queryKey: ['user', user.id] });
      queryClient.invalidateQueries({ queryKey: ['users'] });
    },
    onError: (e) => setError(e instanceof Error ? e.message : '제재를 적용하지 못했습니다.'),
  });

  const data = detail.data;

  return (
    <div className="fixed inset-0 z-50 flex items-center justify-center bg-black/35 px-6 py-10">
      <Card className="max-h-full w-full max-w-2xl overflow-y-auto p-6">
        <div className="flex items-start justify-between">
          <div>
            <p className="eyebrow">회원 상세</p>
            <h2 className="mt-1 font-serif text-[22px] font-bold">{user.nickname}</h2>
            <p className="font-mono text-[12px] text-[var(--color-faint)]">
              @{user.handle} · ID {user.id}
            </p>
          </div>
          <Button variant="ghost" onClick={onClose}>
            닫기
          </Button>
        </div>

        <div className="mt-5 grid grid-cols-4 gap-3">
          <Metric label="세션" value={data?.totalSessions ?? 0} />
          <Metric label="총 독서" value={formatDuration(data?.totalDurationSec)} />
          <Metric label="리뷰" value={data?.reviewCount ?? 0} />
          <Metric label="모임" value={data?.clubCount ?? 0} />
        </div>

        <div className="mt-5 rounded-lg border border-[var(--color-line)] px-4 py-3">
          <p className="eyebrow">이메일</p>
          <p className="mt-1 font-mono text-[13px]">{data?.email ?? '—'}</p>
          {!appliedReason ? (
            <form
              className="mt-3 flex items-end gap-2"
              onSubmit={(e) => {
                e.preventDefault();
                if (revealReason.trim()) setAppliedReason(revealReason.trim());
              }}
            >
              <div className="flex-1">
                <Input
                  label="전체 보기 사유"
                  value={revealReason}
                  onChange={(e) => setRevealReason(e.target.value)}
                  placeholder="예: 계정 도용 신고 확인"
                  hint="열람 자체가 감사 로그에 남습니다."
                />
              </div>
              <Button type="submit" variant="outline">
                열람
              </Button>
            </form>
          ) : (
            <p className="mt-2 font-mono text-[11px] text-[var(--color-warn)]">
              사유 &ldquo;{appliedReason}&rdquo; 로 열람 기록됨
            </p>
          )}
        </div>

        <section className="mt-6">
          <p className="eyebrow">제재 이력</p>
          {(data?.sanctions.length ?? 0) === 0 ? (
            <p className="mt-2 text-[13px] text-[var(--color-muted)]">제재 이력이 없습니다.</p>
          ) : (
            <ul className="mt-2 divide-y divide-[var(--color-line)] rounded-lg border border-[var(--color-line)]">
              {data!.sanctions.map((item) => (
                <li key={item.id} className="px-4 py-2.5">
                  <div className="flex items-center gap-2">
                    <Tag tone={item.releasedAt ? 'neutral' : 'danger'}>{item.type}</Tag>
                    <span className="font-mono text-[11px] text-[var(--color-faint)]">
                      {formatDateTime(item.startsAt)}
                      {item.endsAt ? ` → ${formatDateTime(item.endsAt)}` : ' (영구)'}
                      {item.releasedAt ? ' · 해제됨' : ''}
                    </span>
                  </div>
                  <p className="mt-1 text-[13px]">{item.reason}</p>
                </li>
              ))}
            </ul>
          )}
        </section>

        <section className="mt-6 rounded-lg border border-[var(--color-line)] p-4">
          <p className="eyebrow">제재 적용</p>
          <div className="mt-3 grid grid-cols-2 gap-3">
            <Select
              label="종류"
              value={sanctionType}
              onChange={(e) => setSanctionType(e.target.value as SanctionType)}
            >
              <option value="WARN">경고</option>
              <option value="WRITE_BAN">쓰기 정지</option>
              <option value="SUSPEND">이용 정지</option>
              <option value="TERMINATE">영구 정지</option>
            </Select>
            <Input
              label="기간(일)"
              value={durationDays}
              onChange={(e) => setDurationDays(e.target.value.replace(/\D/g, ''))}
              hint="비우면 영구"
            />
          </div>
          <div className="mt-3">
            <Input
              label="사유 (필수)"
              value={sanctionReason}
              onChange={(e) => setSanctionReason(e.target.value)}
              placeholder="어떤 위반인지 구체적으로"
            />
          </div>
          {error ? (
            <p className="mt-2 font-mono text-[11.5px] text-[var(--color-danger)]">{error}</p>
          ) : null}
          <div className="mt-4 flex justify-end">
            <Button
              variant="danger"
              disabled={!sanctionReason.trim() || sanction.isPending}
              onClick={() => sanction.mutate()}
            >
              {sanction.isPending ? '적용 중…' : '제재 적용'}
            </Button>
          </div>
        </section>
      </Card>
    </div>
  );
}

function Metric({ label, value }: { label: string; value: number | string }) {
  return (
    <div className="rounded-lg bg-[var(--color-surface-alt)] px-3 py-2.5">
      <p className="eyebrow">{label}</p>
      <p className="numeral mt-1 text-[15px]">{value}</p>
    </div>
  );
}
