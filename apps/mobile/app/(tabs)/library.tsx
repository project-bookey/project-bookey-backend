import { useQuery } from '@tanstack/react-query';
import { useRouter } from 'expo-router';
import { useState } from 'react';
import { FlatList, Pressable, StyleSheet, Text, View } from 'react-native';

import { libraryApi } from '@/api/endpoints';
import type { ReadingRecord, ReadingStatus } from '@/api/types';
import { BookCover } from '@/components/BookCover';
import {
  Button, EmptyState, Loading, Numeral, ProgressBar, Screen, Segmented, formatDuration, percent,
} from '@/components/ui';
import { colors, hairline, spacing, statusLabel, type, layout } from '@/theme';

const FILTERS: { value: ReadingStatus; label: string }[] = [
  { value: 'READING', label: '읽는 중' },
  { value: 'WANT_TO_READ', label: '읽고 싶은' },
  { value: 'FINISHED', label: '완독' },
  { value: 'ABANDONED', label: '하차' },
];

/** 탭 2. 서재 — 상태별 필터 (§6) */
export default function LibraryScreen() {
  const router = useRouter();
  const [status, setStatus] = useState<ReadingStatus>('READING');

  const summary = useQuery({ queryKey: ['library', 'summary'], queryFn: libraryApi.summary });
  const list = useQuery({
    queryKey: ['library', status],
    queryFn: () => libraryApi.list(status),
  });

  const counts: Record<ReadingStatus, number> = {
    READING: summary.data?.reading ?? 0,
    WANT_TO_READ: summary.data?.wantToRead ?? 0,
    FINISHED: summary.data?.finished ?? 0,
    ABANDONED: summary.data?.abandoned ?? 0,
    PAUSED: summary.data?.paused ?? 0,
  };

  return (
    <Screen>
      <View style={styles.header}>
        <Segmented
          options={FILTERS.map((f) => ({ value: f.value, label: `${f.label} ${counts[f.value]}` }))}
          value={status}
          onChange={setStatus}
        />
      </View>

      {list.isLoading ? <Loading /> : null}

      <FlatList
        data={list.data?.content ?? []}
        keyExtractor={(item) => String(item.id)}
        contentContainerStyle={styles.listContent}
        ItemSeparatorComponent={() => <View style={styles.separator} />}
        refreshing={list.isFetching}
        onRefresh={() => {
          list.refetch();
          summary.refetch();
        }}
        ListEmptyComponent={
          list.isLoading ? null : (
            <EmptyState
              title={`${statusLabel[status]} 책이 없어요`}
              description={
                status === 'ABANDONED'
                  ? '하차도 기록입니다. 맞지 않는 책을 내려놓는 것도 독서의 일부예요.'
                  : '검색해서 서재에 담아보세요.'
              }
              action={<Button label="책 찾기" onPress={() => router.push('/search')} />}
            />
          )
        }
        renderItem={({ item }) => (
          <LibraryRow
            record={item}
            onPress={() => router.push(`/book/${item.book?.id}?recordId=${item.id}`)}
          />
        )}
      />

      <View style={styles.fabBar}>
        <Button label="+ 책 추가" onPress={() => router.push('/search')} />
      </View>
    </Screen>
  );
}

function LibraryRow({ record, onPress }: { record: ReadingRecord; onPress: () => void }) {
  const hasPages = record.progress.totalPages > 0;
  return (
    <Pressable style={styles.row} onPress={onPress}>
      <BookCover url={record.book?.coverUrl} title={record.book?.title} width={44} />
      <View style={styles.rowBody}>
        <View style={styles.rowHead}>
          <Text numberOfLines={1} style={styles.rowTitle}>{record.book?.title}</Text>
          {record.round > 1 ? <Text style={styles.round}>{record.round}회독</Text> : null}
        </View>
        <Text numberOfLines={1} style={styles.rowAuthor}>{record.book?.author ?? '저자 미상'}</Text>

        {record.status === 'FINISHED' ? (
          <Text style={styles.rowMeta}>
            완독 {record.finishedAt?.slice(0, 10)} · {formatDuration(record.progress.totalDurationSec)}
          </Text>
        ) : record.status === 'ABANDONED' ? (
          <Text style={styles.rowMeta}>하차 · {record.abandonReason ?? '사유 없음'}</Text>
        ) : (
          <View style={styles.rowProgress}>
            <ProgressBar value={record.progress.completionRate} height={4} />
            <Numeral style={styles.rowNumeral}>
              {hasPages
                ? `${record.progress.currentPage}/${record.progress.totalPages} · ${percent(record.progress.completionRate)}`
                : formatDuration(record.progress.totalDurationSec)}
            </Numeral>
          </View>
        )}
      </View>
    </Pressable>
  );
}

const styles = StyleSheet.create({
  header: { ...layout.content, padding: spacing.lg, paddingBottom: spacing.md },
  listContent: { ...layout.content, paddingHorizontal: spacing.lg, paddingBottom: 96 },
  separator: { height: hairline, backgroundColor: colors.line },
  row: { flexDirection: 'row', gap: spacing.md, paddingVertical: spacing.md },
  rowBody: { flex: 1, gap: 3, justifyContent: 'center' },
  rowHead: { flexDirection: 'row', alignItems: 'center', gap: spacing.sm },
  rowTitle: { ...type.subtitle, color: colors.ink, flexShrink: 1 },
  round: { ...type.caption, color: colors.textFaint },
  rowAuthor: { ...type.caption, color: colors.textMuted },
  rowMeta: { ...type.caption, color: colors.textFaint, marginTop: 2 },
  rowProgress: { gap: 4, marginTop: 4 },
  rowNumeral: { fontSize: 11.5, color: colors.textFaint },
  fabBar: {
    position: 'absolute',
    left: spacing.lg,
    right: spacing.lg,
    bottom: spacing.lg,
    maxWidth: 560 - spacing.lg * 2,
    alignSelf: 'center',
  },
});
