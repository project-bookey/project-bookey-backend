'use client';

import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { useState } from 'react';

import { clubsApi } from '@/lib/endpoints';
import type { ClubRow, ClubStatus } from '@/lib/types';
import { PageHeader, Shell } from '@/components/Shell';
import { Button, Card, Empty, Input, Select, Table, Tag, formatDateTime } from '@/components/ui';

const STATUS_TONE: Record<ClubStatus, 'neutral' | 'accent'> = {
  RECRUITING: 'accent',
  ACTIVE: 'accent',
  ENDED: 'neutral',
  ARCHIVED: 'neutral',
};

/** 모임 운영 — 신고된 모임 처리, 코드 강제 회전, 강제 해산 (§F13). */
export default function ClubsPage() {
  const [keyword, setKeyword] = useState('');
  const [query, setQuery] = useState('');
  const [status, setStatus] = useState<ClubStatus | ''>('');
  const [action, setAction] = useState<{ club: ClubRow; kind: 'end' | 'rotate' } | null>(null);

  const clubs = useQuery({
    queryKey: ['clubs', query, status],
    queryFn: () => clubsApi.list(query || undefined, status || undefined),
  });

  return (
    <Shell>
      <PageHeader title="모임" description="운영 중인 독서 모임과 초대 코드를 관리합니다." />

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
              placeholder="모임 이름"
            />
          </div>
          <div className="w-40">
            <Select
              label="상태"
              value={status}
              onChange={(e) => setStatus(e.target.value as ClubStatus | '')}
            >
              <option value="">전체</option>
              <option value="RECRUITING">모집 중</option>
              <option value="ACTIVE">진행 중</option>
              <option value="ENDED">종료</option>
              <option value="ARCHIVED">보관</option>
            </Select>
          </div>
          <Button type="submit">검색</Button>
        </form>

        <Card>
          {(clubs.data?.content.length ?? 0) === 0 ? (
            <Empty>조건에 맞는 모임이 없습니다.</Empty>
          ) : (
            <Table head={['모임', '호스트', '코드', '인원', '기간', '토론', '상태', '']}>
              {clubs.data!.content.map((club) => (
                <tr key={club.id} className="border-b border-[var(--color-line)] last:border-0">
                  <td className="max-w-xs px-4 py-3">
                    <p className="truncate text-[14px] font-bold">{club.name}</p>
                    <p className="font-mono text-[10.5px] text-[var(--color-faint)]">
                      {formatDateTime(club.createdAt)} 생성
                    </p>
                  </td>
                  <td className="px-4 py-3 text-[13px]">{club.ownerNickname ?? '—'}</td>
                  <td className="numeral px-4 py-3 text-[12px] tracking-widest">{club.joinCode}</td>
                  <td className="numeral px-4 py-3 text-[12px]">
                    {club.memberCount}/{club.memberLimit}
                  </td>
                  <td className="px-4 py-3 font-mono text-[11px] text-[var(--color-muted)]">
                    {club.startsAt} → {club.endsAt}
                  </td>
                  <td className="numeral px-4 py-3 text-[12px]">{club.postCount}</td>
                  <td className="px-4 py-3">
                    <Tag tone={STATUS_TONE[club.status]}>{club.status}</Tag>
                  </td>
                  <td className="px-4 py-3 text-right whitespace-nowrap">
                    <Button
                      variant="ghost"
                      onClick={() => setAction({ club, kind: 'rotate' })}
                      className="mr-1"
                    >
                      코드 회전
                    </Button>
                    {club.status !== 'ENDED' && club.status !== 'ARCHIVED' ? (
                      <Button variant="danger" onClick={() => setAction({ club, kind: 'end' })}>
                        해산
                      </Button>
                    ) : null}
                  </td>
                </tr>
              ))}
            </Table>
          )}
        </Card>
      </div>

      {action ? (
        <ActionDialog
          club={action.club}
          kind={action.kind}
          onClose={() => setAction(null)}
        />
      ) : null}
    </Shell>
  );
}

function ActionDialog({ club, kind, onClose }: {
  club: ClubRow;
  kind: 'end' | 'rotate';
  onClose: () => void;
}) {
  const queryClient = useQueryClient();
  const [reason, setReason] = useState('');
  const [result, setResult] = useState<string | null>(null);
  const [error, setError] = useState<string | null>(null);

  const run = useMutation({
    mutationFn: async () => {
      if (kind === 'end') {
        await clubsApi.forceEnd(club.id, reason.trim());
        return null;
      }
      const rotated = await clubsApi.rotateCode(club.id, reason.trim());
      return rotated.joinCode;
    },
    onSuccess: (code) => {
      queryClient.invalidateQueries({ queryKey: ['clubs'] });
      if (code) {
        setResult(code);
      } else {
        onClose();
      }
    },
    onError: (e) => setError(e instanceof Error ? e.message : '처리하지 못했습니다.'),
  });

  return (
    <div className="fixed inset-0 z-50 flex items-center justify-center bg-black/35 px-6">
      <Card className="w-full max-w-md p-6">
        <p className="eyebrow">{kind === 'end' ? '모임 강제 해산' : '초대 코드 강제 회전'}</p>
        <h2 className="mt-1 font-serif text-[19px] font-bold">{club.name}</h2>
        <p className="mt-2 text-[13px] text-[var(--color-muted)]">
          {kind === 'end'
            ? '멤버들의 개인 독서 기록은 유지되고, 모임만 종료 처리됩니다.'
            : '기존 코드는 즉시 무효가 됩니다. 이미 참가한 멤버는 영향을 받지 않습니다.'}
        </p>

        {result ? (
          <div className="mt-4 rounded-lg bg-[var(--color-surface-alt)] px-4 py-3">
            <p className="eyebrow">새 초대 코드</p>
            <p className="numeral mt-1 text-[22px] tracking-[0.3em]">{result}</p>
          </div>
        ) : (
          <div className="mt-4">
            <Input
              label="사유 (필수)"
              value={reason}
              onChange={(e) => setReason(e.target.value)}
              placeholder="감사 로그에 남습니다"
            />
          </div>
        )}

        {error ? (
          <p className="mt-3 font-mono text-[11.5px] text-[var(--color-danger)]">{error}</p>
        ) : null}

        <div className="mt-6 flex justify-end gap-2">
          <Button variant="ghost" onClick={onClose}>
            {result ? '닫기' : '취소'}
          </Button>
          {!result ? (
            <Button
              variant={kind === 'end' ? 'danger' : 'primary'}
              disabled={!reason.trim() || run.isPending}
              onClick={() => run.mutate()}
            >
              {run.isPending ? '처리 중…' : kind === 'end' ? '해산' : '회전'}
            </Button>
          ) : null}
        </div>
      </Card>
    </div>
  );
}
