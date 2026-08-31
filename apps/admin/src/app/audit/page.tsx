'use client';

import { useQuery } from '@tanstack/react-query';
import { useState } from 'react';

import { auditApi } from '@/lib/endpoints';
import { PageHeader, Shell } from '@/components/Shell';
import { Button, Card, Empty, Input, Table, Tag, formatDateTime } from '@/components/ui';

/** 감사 로그 — 누가 · 언제 · 무엇을 · 왜 (§F13). */
export default function AuditPage() {
  const [action, setAction] = useState('');
  const [query, setQuery] = useState('');

  const logs = useQuery({
    queryKey: ['audit', query],
    queryFn: () => auditApi.list(undefined, query || undefined),
  });

  return (
    <Shell>
      <PageHeader
        title="감사 로그"
        description="변경뿐 아니라 개인정보 열람도 기록됩니다."
      />

      <div className="px-7 py-6">
        <form
          className="mb-4 flex items-end gap-3"
          onSubmit={(e) => {
            e.preventDefault();
            setQuery(action.trim());
          }}
        >
          <div className="w-72">
            <Input
              label="액션 필터"
              value={action}
              onChange={(e) => setAction(e.target.value)}
              placeholder="예: SANCTION_SUSPEND, VIEW_USER_PII"
            />
          </div>
          <Button type="submit">조회</Button>
        </form>

        <Card>
          {(logs.data?.content.length ?? 0) === 0 ? (
            <Empty>기록이 없습니다.</Empty>
          ) : (
            <Table head={['시각', '관리자', '액션', '대상', '사유', 'IP']}>
              {logs.data!.content.map((log) => (
                <tr key={log.id} className="border-b border-[var(--color-line)] last:border-0">
                  <td className="px-4 py-2.5 font-mono text-[11px] whitespace-nowrap text-[var(--color-muted)]">
                    {formatDateTime(log.createdAt)}
                  </td>
                  <td className="numeral px-4 py-2.5 text-[12px]">#{log.adminId}</td>
                  <td className="px-4 py-2.5">
                    <Tag tone={log.action.includes('PII') ? 'warn' : 'neutral'}>{log.action}</Tag>
                  </td>
                  <td className="px-4 py-2.5 font-mono text-[11px] text-[var(--color-muted)]">
                    {log.targetType ? `${log.targetType} #${log.targetId ?? '-'}` : '—'}
                  </td>
                  <td className="max-w-xs px-4 py-2.5 text-[13px]">
                    <span className="line-clamp-1">{log.reason ?? '—'}</span>
                  </td>
                  <td className="px-4 py-2.5 font-mono text-[11px] text-[var(--color-faint)]">
                    {log.ip ?? '—'}
                  </td>
                </tr>
              ))}
            </Table>
          )}
        </Card>
      </div>
    </Shell>
  );
}
