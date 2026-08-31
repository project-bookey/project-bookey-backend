import { useQuery } from '@tanstack/react-query';
import { useRouter } from 'expo-router';
import { Pressable, RefreshControl, ScrollView, StyleSheet, Text, View } from 'react-native';

import { libraryApi, notificationApi, statsApi } from '@/api/endpoints';
import type { ReadingRecord } from '@/api/types';
import { BookCover } from '@/components/BookCover';
import {
  Button, Card, EmptyState, Loading, Numeral, ProgressBar, Rule, Screen, SectionHeader, Tag,
  formatDuration, formatRelative, percent,
} from '@/components/ui';
import { useAuth } from '@/store/auth';
import { colors, fonts, hairline, lagStyle, spacing, type, layout } from '@/theme';

/** 탭 1. 홈(오늘) — 읽는 중 카드 · 독서 시작 · 스트릭 · 재촉 배너 (§6) */
export default function HomeScreen() {
  const router = useRouter();
  const user = useAuth((s) => s.user);

  const reading = useQuery({
    queryKey: ['library', 'READING'],
    queryFn: () => libraryApi.list('READING'),
  });
  const stats = useQuery({ queryKey: ['stats', 30], queryFn: () => statsApi.summary(30) });
  const notifications = useQuery({ queryKey: ['notifications'], queryFn: notificationApi.list });

  const records = reading.data?.content ?? [];
  const lagging = records.filter((r) => r.progress.lagLevel !== 'L0_NORMAL');
  const refreshing = reading.isFetching || stats.isFetching;

  return (
    <Screen>
      <ScrollView
        contentContainerStyle={styles.container}
        refreshControl={
          <RefreshControl
            refreshing={refreshing}
            onRefresh={() => {
              reading.refetch();
              stats.refetch();
              notifications.refetch();
            }}
          />
        }
      >
        <View style={styles.greeting}>
          <Text style={styles.greetingText}>{greetingFor(new Date())}, {user?.nickname ?? ''}</Text>
          <View style={styles.streakRow}>
            <Numeral style={styles.streakNumber}>{stats.data?.currentStreakDays ?? 0}</Numeral>
            <Text style={styles.streakLabel}>일 연속</Text>
            <View style={styles.streakDot} />
            <Text style={styles.streakLabel}>
              오늘 {formatDuration(stats.data?.todayDurationSec ?? 0)}
            </Text>
          </View>
        </View>

        {lagging.length > 0 ? (
          <Pressable
            style={styles.nudgeBanner}
            onPress={() => router.push(`/timer?recordId=${lagging[0].id}`)}
          >
            <Text style={styles.nudgeEyebrow}>재촉</Text>
            <Text style={styles.nudgeTitle}>
              『{lagging[0].book?.title}』 {lagging[0].progress.daysSinceLastRead ?? 0}일째 멈춰 있어요
            </Text>
            <Text style={styles.nudgeBody}>
              {lagging[0].progress.remainingPages}쪽 남음 · 지금 5분이면 한 걸음 나아갑니다
            </Text>
          </Pressable>
        ) : null}

        <View>
          <SectionHeader
            title="읽는 중"
            action={
              <Button
                label="책 추가"
                variant="ghost"
                size="sm"
                onPress={() => router.push('/search')}
              />
            }
          />
          {reading.isLoading ? <Loading /> : null}
          {!reading.isLoading && records.length === 0 ? (
            <EmptyState
              title="아직 읽는 중인 책이 없어요"
              description="책을 등록하고 완독 목표일을 정하면, 페이스가 밀릴 때 알려드립니다."
              action={<Button label="책 찾기" onPress={() => router.push('/search')} />}
            />
          ) : null}
          <View style={{ gap: spacing.md }}>
            {records.map((record) => (
              <ReadingCard
                key={record.id}
                record={record}
                onStart={() => router.push(`/timer?recordId=${record.id}`)}
                onOpen={() => router.push(`/book/${record.book?.id}?recordId=${record.id}`)}
              />
            ))}
          </View>
        </View>

        {(notifications.data?.content.length ?? 0) > 0 ? (
          <View>
            <SectionHeader title="알림" />
            <Card style={{ padding: 0 }}>
              {notifications.data!.content.slice(0, 4).map((n, index) => (
                <View key={n.id}>
                  {index > 0 ? <Rule /> : null}
                  <View style={styles.notificationRow}>
                    <Text style={styles.notificationTitle}>{n.title}</Text>
                    <Text style={styles.notificationBody}>{n.body}</Text>
                    <Text style={styles.notificationMeta}>{formatRelative(n.sentAt ?? n.scheduledAt)}</Text>
                  </View>
                </View>
              ))}
            </Card>
          </View>
        ) : null}
      </ScrollView>
    </Screen>
  );
}

function ReadingCard({ record, onStart, onOpen }: {
  record: ReadingRecord;
  onStart: () => void;
  onOpen: () => void;
}) {
  const lag = lagStyle[record.progress.lagLevel] ?? lagStyle.L0_NORMAL;
  const hasPages = record.progress.totalPages > 0;

  return (
    <Card style={{ padding: 0 }}>
      <Pressable style={styles.readingTop} onPress={onOpen}>
        <BookCover url={record.book?.coverUrl} title={record.book?.title} width={54} />
        <View style={styles.readingInfo}>
          <Text numberOfLines={2} style={styles.bookTitle}>{record.book?.title}</Text>
          <Text numberOfLines={1} style={styles.bookAuthor}>{record.book?.author ?? '저자 미상'}</Text>
          <View style={styles.tagRow}>
            {record.progress.lagLevel !== 'L0_NORMAL' ? (
              <Tag label={lag.label} fg={lag.fg} bg={lag.bg} />
            ) : null}
            {record.targetFinishDate ? (
              <Tag label={`목표 ${record.targetFinishDate.slice(5).replace('-', '/')}`} />
            ) : null}
          </View>
        </View>
      </Pressable>

      <View style={styles.readingProgress}>
        <View style={styles.progressLabels}>
          <Numeral style={styles.pageNumber}>
            {record.progress.currentPage}
            <Text style={styles.pageTotal}>
              {hasPages ? ` / ${record.progress.totalPages}쪽` : '쪽'}
            </Text>
          </Numeral>
          <Numeral style={styles.percentNumber}>
            {hasPages ? percent(record.progress.completionRate) : formatDuration(record.progress.totalDurationSec)}
          </Numeral>
        </View>
        <ProgressBar value={record.progress.completionRate} />
        <View style={styles.metaRow}>
          <Text style={styles.metaText}>
            {record.progress.estimatedFinishDate
              ? `예상 완독 ${record.progress.estimatedFinishDate.slice(5).replace('-', '/')}`
              : '페이스 데이터 부족'}
          </Text>
          <Text style={styles.metaText}>{formatRelative(record.lastReadAt)}</Text>
        </View>
      </View>

      <Pressable style={styles.startButton} onPress={onStart}>
        <Text style={styles.startButtonLabel}>독서 시작</Text>
      </Pressable>
    </Card>
  );
}

function greetingFor(now: Date): string {
  const hour = now.getHours();
  if (hour < 6) return '늦은 밤이에요';
  if (hour < 12) return '좋은 아침이에요';
  if (hour < 18) return '좋은 오후예요';
  return '좋은 저녁이에요';
}

const styles = StyleSheet.create({
  container: { ...layout.content, padding: spacing.lg, gap: spacing.xl, paddingBottom: spacing.xxl },
  greeting: { gap: spacing.sm },
  greetingText: { ...type.display, color: colors.ink },
  streakRow: { flexDirection: 'row', alignItems: 'baseline', gap: spacing.xs },
  streakNumber: { fontSize: 15, fontWeight: '700', color: colors.ink },
  streakLabel: { ...type.caption, color: colors.textMuted },
  streakDot: {
    width: 3,
    height: 3,
    backgroundColor: colors.textFaint,
    marginHorizontal: spacing.xs,
    alignSelf: 'center',
  },
  nudgeBanner: {
    borderLeftWidth: 3,
    borderLeftColor: colors.ink,
    borderRadius: 10,
    backgroundColor: colors.surfaceAlt,
    padding: spacing.lg,
    gap: spacing.xs,
  },
  nudgeEyebrow: { ...type.eyebrow, color: colors.textFaint },
  nudgeTitle: { ...type.subtitle, color: colors.ink },
  nudgeBody: { ...type.caption, color: colors.textMuted },
  readingTop: { flexDirection: 'row', gap: spacing.md, padding: spacing.lg },
  readingInfo: { flex: 1, gap: 3 },
  bookTitle: { ...type.subtitle, color: colors.ink },
  bookAuthor: { ...type.caption, color: colors.textMuted },
  tagRow: { flexDirection: 'row', gap: spacing.xs, marginTop: spacing.xs, flexWrap: 'wrap' },
  readingProgress: { paddingHorizontal: spacing.lg, paddingBottom: spacing.lg, gap: spacing.sm },
  progressLabels: { flexDirection: 'row', justifyContent: 'space-between', alignItems: 'baseline' },
  pageNumber: { fontSize: 17, fontWeight: '700', color: colors.ink },
  pageTotal: { fontSize: 12, fontWeight: '400', color: colors.textFaint },
  percentNumber: { fontSize: 13, color: colors.textMuted },
  metaRow: { flexDirection: 'row', justifyContent: 'space-between' },
  metaText: { ...type.caption, color: colors.textFaint },
  startButton: {
    borderTopWidth: hairline,
    borderTopColor: colors.line,
    paddingVertical: spacing.md,
    alignItems: 'center',
    backgroundColor: colors.ink,
  },
  startButtonLabel: { ...type.label, color: '#FFFFFF', letterSpacing: 0.3 },
  notificationRow: { padding: spacing.lg, gap: 3 },
  notificationTitle: { ...type.label, color: colors.ink },
  notificationBody: { ...type.caption, color: colors.textMuted, lineHeight: 17 },
  notificationMeta: { ...type.caption, color: colors.textFaint, marginTop: 2 },
});
