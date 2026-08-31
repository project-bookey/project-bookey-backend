'use client';

import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { useState } from 'react';

import { moderationApi } from '@/lib/endpoints';
import type { ModerationResolution, ModerationRow, ModerationStatus, SanctionType } from '@/lib/types';
import { PageHeader, Shell } from '@/components/Shell';
import { Button, Card, Empty, Input, Select, Table, Tag, formatDateTime, remainingSla } from '@/components/ui';

const RESOLUTIONS: { value: ModerationResolution; label: string; description: string }[] = [
  { value: 'KEEP', label: '유지', description: '문제 없음 — 다시 노출' },
  { value: 'HIDE', label: '숨김', description: '비노출 처리' },
  { value: 'DELETE', label: '삭제', description: '내용 삭제' },
  { value: 'SANCTION', label: '숨김 + 제재', description: '작성자에게 제재 적용' },
];

/** 신고 큐 — SLA 48h, 우선순위 순 (§F13 · §8.3). */
export default function ModerationPage() {
  const queryClient = useQueryClient();
  const [status, setStatus] = useState<ModerationStatus | ''>('PENDING');
  const [selected, setSelected] = useState<ModerationRow | null>(null);

  const queue = useQuery({
    queryKey: ['moderation', status],
    queryFn: () => moderationApi.queue(status || undefined),
  });

  return (
    <Shell>
      <PageHeader
        title="신고 큐"
        description="접수 48시간 안에 1차 판정합니다. 우선순위가 높은 건이 위에 옵니다."
        action={
          <div className="w-40">
            <Select value={status} onChange={(e) => setStatus(e.target.value as ModerationStatus | '')}>
              <option value="">전체</option>
              <option value="PENDING">대기</option>
              <option value="IN_REVIEW">검토 중</option>
              <option value="RESOLVED">처리 완료</option>
            </Select>
          </div>
        }
      />

      <div className="px-7 py-6">
        <Card>
          {(queue.data?.content.length ?? 0) === 0 ? (
            <Empty>표시할 신고가 없습니다.</Empty>
          ) : (
            <Table head={['대상', '내용', '작성자', '신고', 'SLA', '상태', '']}>
              {queue.data!.content.map((ticket) => {
                const sla = remainingSla(ticket.slaDueAt);
                return (
                  <tr key={ticket.id} className="border-b border-[var(--color-line)] last:border-0">
                    <td className="px-4 py-3">
                      <Tag tone={ticket.priority <= 1 ? 'danger' : 'neutral'}>
                        {ticket.sourceType}
                      </Tag>
                    </td>
                    <td className="max-w-sm px-4 py-3">
                      <p className="truncate text-[13.5px]">{ticket.contentPreview ?? '(내용 없음)'}</p>
                      <p className="font-mono text-[10.5px] text-[var(--color-faint)]">
                        사유 {ticket.reason}
                      </p>
                    </td>
                    <td className="px-4 py-3 text-[13px]">{ticket.authorNickname ?? '—'}</td>
                    <td className="numeral px-4 py-3 text-[12px]">{ticket.reportCount}</td>
                    <td
                      className={`numeral px-4 py-3 text-[11.5px] ${
                        sla.overdue ? 'text-[var(--color-danger)]' : 'text-[var(--color-muted)]'
                      }`}
                    >
                      {sla.label}
                    </td>
                    <td className="px-4 py-3">
                      <Tag tone={ticket.status === 'RESOLVED' ? 'accent' : 'neutral'}>
                        {ticket.status}
                      </Tag>
                    </td>
                    <td className="px-4 py-3 text-right">
                      {ticket.status !== 'RESOLVED' ? (
                        <Button variant="outline" onClick={() => setSelected(ticket)}>
                          처리
                        </Button>
                      ) : null}
                    </td>
                  </tr>
                );
              })}
            </Table>
          )}
        </Card>
      </div>

      {selected ? (
        <ResolveDialog
          ticket={selected}
          onClose={() => setSelected(null)}
          onDone={() => {
            setSelected(null);
            queryClient.invalidateQueries({ queryKey: ['moderation'] });
            queryClient.invalidateQueries({ queryKey: ['dashboard'] });
          }}
        />
      ) : null}
    </Shell>
  );
}

function ResolveDialog({ ticket, onClose, onDone }: {
  ticket: ModerationRow;
  onClose: () => void;
  onDone: () => void;
}) {
  const [resolution, setResolution] = useState<ModerationResolution>('HIDE');
  const [note, setNote] = useState('');
  const [sanctionType, setSanctionType] = useState<SanctionType>('WRITE_BAN');
  const [durationDays, setDurationDays] = useState('7');
  const [error, setError] = useState<string | null>(null);

  const resolve = useMutation({
    mutationFn: () =>
      moderationApi.resolve(ticket.id, {
        resolution,
        note: note.trim() || undefined,
        sanction:
          resolution === 'SANCTION'
            ? {
                type: sanctionType,
                reason: note.trim() || '신고 처리에 따른 제재',
                durationDays: durationDays ? Number(durationDays) : undefined,
              }
            : undefined,
      }),
    onSuccess: onDone,
    onError: (e) => setError(e instanceof Error ? e.message : '처리하지 못했습니다.'),
  });

  return (
    <div className="fixed inset-0 z-50 flex items-center justify-center bg-black/35 px-6">
      <Card className="w-full max-w-lg p-6">
        <p className="eyebrow">신고 처리</p>
        <h2 className="mt-2 font-serif text-[20px] font-bold">
          {ticket.sourceType} #{ticket.sourceId}
        </h2>
        <p className="mt-2 rounded-lg bg-[var(--color-surface-alt)] px-3 py-2.5 text-[13.5px] leading-relaxed">
          {ticket.contentPreview ?? '(내용 없음)'}
        </p>
        <p className="mt-2 font-mono text-[11px] text-[var(--color-faint)]">
          작성자 {ticket.authorNickname ?? '—'} · 신고 {ticket.reportCount}건 · 접수{' '}
          {formatDateTime(ticket.slaDueAt)} 마감
        </p>

        <div className="mt-5 flex flex-col gap-2">
          {RESOLUTIONS.map((option) => (
            <label
              key={option.value}
              className={`flex cursor-pointer items-start gap-3 rounded-lg border px-3 py-2.5 ${
                resolution === option.value
                  ? 'border-[var(--color-ink)] bg-[var(--color-surface-alt)]'
                  : 'border-[var(--color-line)]'
              }`}
            >
              <input
                type="radio"
                checked={resolution === option.value}
                onChange={() => setResolution(option.value)}
                className="mt-1"
              />
              <span>
                <span className="block font-mono text-[12.5px] font-bold">{option.label}</span>
                <span className="block text-[12.5px] text-[var(--color-muted)]">
                  {option.description}
                </span>
              </span>
            </label>
          ))}
        </div>

        {resolution === 'SANCTION' ? (
          <div className="mt-4 grid grid-cols-2 gap-3">
            <Select
              label="제재 종류"
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
        ) : null}

        <div className="mt-4">
          <Input
            label="처리 사유"
            value={note}
            onChange={(e) => setNote(e.target.value)}
            placeholder="판단 근거를 남겨주세요 — 감사 로그에 기록됩니다"
          />
        </div>

        {error ? (
          <p className="mt-3 font-mono text-[11.5px] text-[var(--color-danger)]">{error}</p>
        ) : null}

        <div className="mt-6 flex justify-end gap-2">
          <Button variant="ghost" onClick={onClose}>
            취소
          </Button>
          <Button onClick={() => resolve.mutate()} disabled={resolve.isPending}>
            {resolve.isPending ? '처리 중…' : '처리 확정'}
          </Button>
        </div>
      </Card>
    </div>
  );
}
