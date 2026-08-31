'use client';

import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { useState } from 'react';

import { reviewsApi } from '@/lib/endpoints';
import type { ReviewRow, VerificationLevel } from '@/lib/types';
import { PageHeader, Shell } from '@/components/Shell';
import { Button, Card, Empty, Input, Select, Tag, formatDateTime } from '@/components/ui';

const LEVEL_LABEL: Record<VerificationLevel, string> = {
  VERIFIED_FULL: '완독 검증',
  VERIFIED_PARTIAL: '부분 검증',
  UNVERIFIED: '미검증',
  FLAGGED: '의심',
};

const LEVEL_TONE: Record<VerificationLevel, 'accent' | 'neutral' | 'warn' | 'danger'> = {
  VERIFIED_FULL: 'accent',
  VERIFIED_PARTIAL: 'neutral',
  UNVERIFIED: 'neutral',
  FLAGGED: 'danger',
};

/** 검증 심사 — 등급 산정 근거(스냅샷)를 보고 수동 조정한다 (§8.2 · §F13). */
export default function ReviewsPage() {
  const [selected, setSelected] = useState<ReviewRow | null>(null);
  const reviews = useQuery({ queryKey: ['reviews'], queryFn: () => reviewsApi.list() });

  return (
    <Shell>
      <PageHeader
        title="검증 심사"
        description="등급은 리뷰 작성 시점에 고정됩니다. 조정하면 사유가 감사 로그에 남습니다."
      />

      <div className="px-7 py-6">
        <Card>
          {(reviews.data?.content.length ?? 0) === 0 ? (
            <Empty>표시할 리뷰가 없습니다.</Empty>
          ) : (
            <ul className="divide-y divide-[var(--color-line)]">
              {reviews.data!.content.map((review) => (
                <li key={review.id} className="px-5 py-4">
                  <div className="flex items-start justify-between gap-4">
                    <div className="min-w-0">
                      <div className="flex flex-wrap items-center gap-2">
                        <Tag tone={LEVEL_TONE[review.verificationLevel]}>
                          {LEVEL_LABEL[review.verificationLevel]}
                        </Tag>
                        <span className="text-[13.5px] font-bold">{review.bookTitle ?? '—'}</span>
                        <span className="font-mono text-[11px] text-[var(--color-faint)]">
                          {review.authorNickname ?? '—'} ·{' '}
                          {review.rating ? `★${review.rating}` : '별점 없음'} ·{' '}
                          {formatDateTime(review.createdAt)}
                        </span>
                        {review.reportCount > 0 ? (
                          <Tag tone="danger">신고 {review.reportCount}</Tag>
                        ) : null}
                      </div>
                      <p className="mt-2 line-clamp-2 text-[13.5px] leading-relaxed">{review.body}</p>
                      {review.verificationSnapshot ? (
                        <p className="mt-1.5 font-mono text-[10.5px] text-[var(--color-faint)]">
                          커버리지{' '}
                          {Math.round((Number(review.verificationSnapshot.coverage) || 0) * 100)}% ·
                          타이머 {String(review.verificationSnapshot.timerSessionCount ?? '—')}회 ·
                          인정 {String(review.verificationSnapshot.verifiedMinutes ?? '—')}분 / 요구{' '}
                          {String(review.verificationSnapshot.requiredMinutes ?? '—')}분
                        </p>
                      ) : null}
                    </div>
                    <Button variant="outline" onClick={() => setSelected(review)}>
                      등급 조정
                    </Button>
                  </div>
                </li>
              ))}
            </ul>
          )}
        </Card>
      </div>

      {selected ? <OverrideDialog review={selected} onClose={() => setSelected(null)} /> : null}
    </Shell>
  );
}

function OverrideDialog({ review, onClose }: { review: ReviewRow; onClose: () => void }) {
  const queryClient = useQueryClient();
  const [level, setLevel] = useState<VerificationLevel>(review.verificationLevel);
  const [reason, setReason] = useState('');
  const [error, setError] = useState<string | null>(null);

  const override = useMutation({
    mutationFn: () => reviewsApi.overrideVerification(review.id, level, reason.trim()),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['reviews'] });
      onClose();
    },
    onError: (e) => setError(e instanceof Error ? e.message : '조정하지 못했습니다.'),
  });

  return (
    <div className="fixed inset-0 z-50 flex items-center justify-center bg-black/35 px-6">
      <Card className="w-full max-w-md p-6">
        <p className="eyebrow">검증 등급 조정</p>
        <h2 className="mt-1 font-serif text-[19px] font-bold">{review.bookTitle}</h2>
        <p className="mt-2 rounded-lg bg-[var(--color-surface-alt)] px-3 py-2.5 text-[13px] leading-relaxed">
          {review.body}
        </p>

        <div className="mt-4">
          <Select
            label="등급"
            value={level}
            onChange={(e) => setLevel(e.target.value as VerificationLevel)}
          >
            {(Object.keys(LEVEL_LABEL) as VerificationLevel[]).map((key) => (
              <option key={key} value={key}>
                {LEVEL_LABEL[key]}
              </option>
            ))}
          </Select>
        </div>

        <div className="mt-3">
          <Input
            label="조정 사유 (필수)"
            value={reason}
            onChange={(e) => setReason(e.target.value)}
            placeholder="예: 재검증 요청 확인, 세션 기록 정상"
          />
        </div>

        {error ? (
          <p className="mt-3 font-mono text-[11.5px] text-[var(--color-danger)]">{error}</p>
        ) : null}

        <div className="mt-6 flex justify-end gap-2">
          <Button variant="ghost" onClick={onClose}>
            취소
          </Button>
          <Button disabled={!reason.trim() || override.isPending} onClick={() => override.mutate()}>
            {override.isPending ? '저장 중…' : '조정'}
          </Button>
        </div>
      </Card>
    </div>
  );
}
