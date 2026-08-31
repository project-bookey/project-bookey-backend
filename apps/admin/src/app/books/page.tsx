'use client';

import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { useState } from 'react';

import { booksApi } from '@/lib/endpoints';
import type { BookRow } from '@/lib/types';
import { PageHeader, Shell } from '@/components/Shell';
import { Button, Card, Empty, Input, Table, Tag } from '@/components/ui';

/** 도서 관리 — 페이지 수 보정이 핵심 (진척도 계산의 기준값). */
export default function BooksPage() {
  const [keyword, setKeyword] = useState('');
  const [query, setQuery] = useState('');
  const [editing, setEditing] = useState<BookRow | null>(null);

  const books = useQuery({
    queryKey: ['books', query],
    queryFn: () => booksApi.list(query || undefined),
  });

  return (
    <Shell>
      <PageHeader
        title="도서"
        description="총 페이지 수는 진척도와 검증 등급의 기준값입니다. 빠진 값을 채워주세요."
      />

      <div className="px-7 py-6">
        <form
          className="mb-4 flex items-end gap-3"
          onSubmit={(e) => {
            e.preventDefault();
            setQuery(keyword.trim());
          }}
        >
          <div className="w-80">
            <Input
              label="검색"
              value={keyword}
              onChange={(e) => setKeyword(e.target.value)}
              placeholder="제목 · 저자 · ISBN"
            />
          </div>
          <Button type="submit">검색</Button>
        </form>

        <Card>
          {(books.data?.content.length ?? 0) === 0 ? (
            <Empty>조건에 맞는 도서가 없습니다.</Empty>
          ) : (
            <Table head={['제목', '저자', '출판사', '페이지', 'ISBN', '출처', '']}>
              {books.data!.content.map((book) => (
                <tr key={book.id} className="border-b border-[var(--color-line)] last:border-0">
                  <td className="max-w-xs px-4 py-3">
                    <p className="truncate text-[14px] font-bold">{book.title}</p>
                    {book.userCreated ? <Tag>사용자 등록</Tag> : null}
                  </td>
                  <td className="px-4 py-3 text-[13px]">{book.author ?? '—'}</td>
                  <td className="px-4 py-3 text-[13px] text-[var(--color-muted)]">
                    {book.publisher ?? '—'}
                  </td>
                  <td className="px-4 py-3">
                    {book.totalPages ? (
                      <span className="numeral text-[12px]">{book.totalPages}</span>
                    ) : (
                      <Tag tone="warn">없음</Tag>
                    )}
                  </td>
                  <td className="px-4 py-3 font-mono text-[11px] text-[var(--color-faint)]">
                    {book.isbn13 ?? '—'}
                  </td>
                  <td className="px-4 py-3">
                    <Tag>{book.source}</Tag>
                  </td>
                  <td className="px-4 py-3 text-right">
                    <Button variant="outline" onClick={() => setEditing(book)}>
                      수정
                    </Button>
                  </td>
                </tr>
              ))}
            </Table>
          )}
        </Card>
      </div>

      {editing ? <EditDialog book={editing} onClose={() => setEditing(null)} /> : null}
    </Shell>
  );
}

function EditDialog({ book, onClose }: { book: BookRow; onClose: () => void }) {
  const queryClient = useQueryClient();
  const [title, setTitle] = useState(book.title);
  const [author, setAuthor] = useState(book.author ?? '');
  const [publisher, setPublisher] = useState(book.publisher ?? '');
  const [totalPages, setTotalPages] = useState(String(book.totalPages ?? ''));
  const [reason, setReason] = useState('');
  const [error, setError] = useState<string | null>(null);

  const update = useMutation({
    mutationFn: () =>
      booksApi.update(book.id, {
        title: title.trim(),
        author: author.trim() || undefined,
        publisher: publisher.trim() || undefined,
        totalPages: totalPages ? Number(totalPages) : undefined,
        reason: reason.trim(),
      }),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['books'] });
      onClose();
    },
    onError: (e) => setError(e instanceof Error ? e.message : '수정하지 못했습니다.'),
  });

  return (
    <div className="fixed inset-0 z-50 flex items-center justify-center bg-black/35 px-6">
      <Card className="w-full max-w-lg p-6">
        <p className="eyebrow">도서 수정</p>
        <h2 className="mt-1 font-serif text-[20px] font-bold">{book.title}</h2>

        <div className="mt-5 flex flex-col gap-3">
          <Input label="제목" value={title} onChange={(e) => setTitle(e.target.value)} />
          <div className="grid grid-cols-2 gap-3">
            <Input label="저자" value={author} onChange={(e) => setAuthor(e.target.value)} />
            <Input label="출판사" value={publisher} onChange={(e) => setPublisher(e.target.value)} />
          </div>
          <Input
            label="총 페이지"
            value={totalPages}
            onChange={(e) => setTotalPages(e.target.value.replace(/\D/g, ''))}
            hint="이 값이 바뀌면 모든 독자의 진행률이 다시 계산됩니다."
          />
          <Input
            label="수정 사유 (필수)"
            value={reason}
            onChange={(e) => setReason(e.target.value)}
            placeholder="예: 알라딘 페이지 수와 실제 도서가 불일치"
          />
        </div>

        {error ? (
          <p className="mt-3 font-mono text-[11.5px] text-[var(--color-danger)]">{error}</p>
        ) : null}

        <div className="mt-6 flex justify-end gap-2">
          <Button variant="ghost" onClick={onClose}>
            취소
          </Button>
          <Button disabled={!reason.trim() || update.isPending} onClick={() => update.mutate()}>
            {update.isPending ? '저장 중…' : '저장'}
          </Button>
        </div>
      </Card>
    </div>
  );
}
