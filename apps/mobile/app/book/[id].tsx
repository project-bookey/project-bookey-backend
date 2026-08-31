import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { useLocalSearchParams, useRouter } from 'expo-router';
import { ScrollView, StyleSheet, Text, View } from 'react-native';

import { bookApi, libraryApi, reviewApi, sessionApi } from '@/api/endpoints';
import type { VerificationLevel } from '@/api/types';
import { BookCover } from '@/components/BookCover';
import {
  Button, Card, Eyebrow, KeyValue, Loading, Numeral, ProgressBar, Rule, Screen, Tag,
  formatDuration, formatRelative, percent,
} from '@/components/ui';
import { colors, hairline, lagStyle, spacing, type, layout } from '@/theme';

const VERIFICATION_LABEL: Record<VerificationLevel, string> = {
  VERIFIED_FULL: '완독 검증',
  VERIFIED_PARTIAL: '부분 검증',
  UNVERIFIED: '미검증',
  FLAGGED: '검토 중',
};

/** 도서 상세 — 메타 + 내 진척 + 세션 로그 + 검증 리뷰 (§6 핵심 화면 3) */
export default function BookDetailScreen() {
  const router = useRouter();
  const queryClient = useQueryClient();
  const { id, recordId } = useLocalSearchParams<{ id: string; recordId?: string }>();
  const bookId = Number(id);
  const rid = recordId ? Number(recordId) : null;

  const book = useQuery({
    queryKey: ['book', bookId],
    queryFn: () => bookApi.detail(bookId),
    enabled: Number.isFinite(bookId),
  });
  const record = useQuery({
    queryKey: ['library', 'record', rid],
    queryFn: () => libraryApi.detail(rid!),
    enabled: rid != null,
  });
  const sessions = useQuery({
    queryKey: ['sessions', rid],
    queryFn: () => sessionApi.listByRecord(rid!),
    enabled: rid != null,
  });
  const verification = useQuery({
    queryKey: ['review', 'preview', rid],
    queryFn: () => reviewApi.preview(rid!),
    enabled: rid != null,
  });
  const reviews = useQuery({
    queryKey: ['book', bookId, 'reviews'],
    queryFn: () => bookApi.reviews(bookId),
    enabled: Number.isFinite(bookId),
  });

  const finish = useMutation({
    mutationFn: () => libraryApi.finish(rid!),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['library'] });
      queryClient.invalidateQueries({ queryKey: ['review', 'preview', rid] });
    },
  });
  const abandon = useMutation({
    mutationFn: () => libraryApi.abandon(rid!, 'NOT_MY_TASTE'),
    onSuccess: () => queryClient.invalidateQueries({ queryKey: ['library'] }),
  });

  if (book.isLoading) {
    return <Screen><Loading /></Screen>;
  }

  const info = book.data?.book;
  const progress = record.data?.progress;
  const lag = progress ? lagStyle[progress.lagLevel] : null;

  return (
    <Screen>
      <ScrollView contentContainerStyle={styles.container}>
        <View style={styles.header}>
          <BookCover url={info?.coverUrl} title={info?.title} width={72} />
          <View style={{ flex: 1, gap: 4 }}>
            <Text style={styles.title}>{info?.title}</Text>
            <Text style={styles.author}>{info?.author ?? '저자 미상'}</Text>
            <Text style={styles.meta}>
              {info?.publisher ?? ''}
              {info?.totalPages ? ` · ${info.totalPages}쪽` : ''}
            </Text>
          </View>
        </View>

        {record.data && progress ? (
          <Card style={{ gap: spacing.md }}>
            <View style={styles.progressHead}>
              <Eyebrow>내 진척</Eyebrow>
              {lag && progress.lagLevel !== 'L0_NORMAL' ? (
                <Tag label={lag.label} fg={lag.fg} bg={lag.bg} />
              ) : null}
            </View>
            <View style={styles.progressNumbers}>
              <Numeral style={styles.currentPage}>{progress.currentPage}</Numeral>
              <Text style={styles.totalPage}>
                {progress.totalPages > 0 ? ` / ${progress.totalPages}쪽` : '쪽'}
              </Text>
              <Numeral style={styles.percent}>{percent(progress.completionRate)}</Numeral>
            </View>
            <ProgressBar value={progress.completionRate} />
            <Rule />
            <KeyValue label="누적 독서시간" value={formatDuration(progress.totalDurationSec)} />
            <KeyValue
              label="최근 7일 페이스"
              value={`${(progress.actualDailyPace ?? 0).toFixed(1)}쪽/일`}
            />
            {progress.requiredDailyPace != null ? (
              <KeyValue
                label="필요 페이스"
                value={`${progress.requiredDailyPace.toFixed(1)}쪽/일`}
              />
            ) : null}
            {progress.estimatedFinishDate ? (
              <KeyValue label="예상 완독일" value={progress.estimatedFinishDate} />
            ) : null}

            <View style={styles.actionRow}>
              <Button
                label="독서 시작"
                style={{ flex: 1 }}
                onPress={() => router.push(`/timer?recordId=${rid}`)}
              />
              {record.data.status !== 'FINISHED' ? (
                <Button
                  label="완독 처리"
                  variant="outline"
                  style={{ flex: 1 }}
                  loading={finish.isPending}
                  onPress={() => finish.mutate()}
                />
              ) : null}
            </View>
            {record.data.status === 'READING' ? (
              <Button
                label="하차하기"
                variant="ghost"
                size="sm"
                loading={abandon.isPending}
                onPress={() => abandon.mutate()}
              />
            ) : null}
          </Card>
        ) : null}

        {verification.data ? (
          <Card style={{ gap: spacing.sm }}>
            <Eyebrow>리뷰 검증 상태</Eyebrow>
            <View style={styles.verifyHead}>
              <Text style={styles.verifyLevel}>
                {VERIFICATION_LABEL[verification.data.expectedLevel]}
              </Text>
              <Text style={styles.verifyHint}>지금 리뷰를 쓰면 받게 될 배지</Text>
            </View>
            <Rule />
            <KeyValue label="읽은 범위" value={percent(verification.data.coverage)} />
            <KeyValue label="타이머 세션" value={`${verification.data.timerSessionCount}회`} />
            <KeyValue
              label="인정 독서시간"
              value={`${verification.data.verifiedMinutes}분 / 최소 ${verification.data.requiredMinutes}분`}
            />
            {verification.data.flags.length > 0 ? (
              <Text style={styles.flags}>신호: {verification.data.flags.join(', ')}</Text>
            ) : null}
          </Card>
        ) : null}

        {sessions.data && sessions.data.length > 0 ? (
          <View>
            <Eyebrow>세션 기록</Eyebrow>
            <Card style={{ marginTop: spacing.sm, padding: 0 }}>
              {sessions.data.slice(0, 8).map((session, index) => (
                <View key={session.id}>
                  {index > 0 ? <Rule /> : null}
                  <View style={styles.sessionRow}>
                    <View style={{ flex: 1 }}>
                      <Numeral style={styles.sessionPages}>
                        {session.startPage ?? 0} → {session.endPage ?? session.startPage ?? 0}쪽
                      </Numeral>
                      <Text style={styles.sessionMeta}>
                        {formatRelative(session.startedAt)} ·{' '}
                        {session.source === 'TIMER' ? '타이머' : '수동'}
                      </Text>
                    </View>
                    <Numeral style={styles.sessionDuration}>
                      {formatDuration(session.durationSec)}
                    </Numeral>
                    {!session.countedForVerification ? <Tag label="검증 제외" /> : null}
                  </View>
                </View>
              ))}
            </Card>
          </View>
        ) : null}

        <View>
          <Eyebrow>리뷰</Eyebrow>
          {(reviews.data?.content.length ?? 0) === 0 ? (
            <Card style={{ marginTop: spacing.sm }}>
              <Text style={styles.emptyReview}>
                아직 리뷰가 없습니다. 완독하면 검증 배지와 함께 첫 리뷰를 남길 수 있어요.
              </Text>
            </Card>
          ) : (
            <Card style={{ marginTop: spacing.sm, padding: 0 }}>
              {reviews.data!.content.map((review, index) => (
                <View key={review.id}>
                  {index > 0 ? <Rule /> : null}
                  <View style={styles.reviewRow}>
                    <View style={styles.reviewHead}>
                      <Text style={styles.reviewAuthor}>{review.authorNickname}</Text>
                      <Tag
                        label={VERIFICATION_LABEL[review.verificationLevel]}
                        fg={review.verificationLevel === 'VERIFIED_FULL' ? colors.accent : undefined}
                        bg={review.verificationLevel === 'VERIFIED_FULL' ? colors.accentSoft : undefined}
                      />
                      {review.rating ? (
                        <Numeral style={styles.rating}>★ {review.rating}</Numeral>
                      ) : null}
                    </View>
                    <Text style={styles.reviewBody}>{review.body}</Text>
                  </View>
                </View>
              ))}
            </Card>
          )}
        </View>
      </ScrollView>
    </Screen>
  );
}

const styles = StyleSheet.create({
  container: { ...layout.content, padding: spacing.lg, gap: spacing.xl, paddingBottom: spacing.xxl },
  header: { flexDirection: 'row', gap: spacing.lg },
  title: { ...type.title, color: colors.ink },
  author: { ...type.body, color: colors.textMuted },
  meta: { ...type.caption, color: colors.textFaint },
  progressHead: { flexDirection: 'row', justifyContent: 'space-between', alignItems: 'center' },
  progressNumbers: { flexDirection: 'row', alignItems: 'baseline' },
  currentPage: { fontSize: 30, fontWeight: '700', color: colors.ink },
  totalPage: { ...type.body, color: colors.textFaint },
  percent: { marginLeft: 'auto', fontSize: 14, color: colors.textMuted },
  actionRow: { flexDirection: 'row', gap: spacing.sm, marginTop: spacing.xs },
  verifyHead: { flexDirection: 'row', alignItems: 'baseline', gap: spacing.sm },
  verifyLevel: { ...type.title, color: colors.ink },
  verifyHint: { ...type.caption, color: colors.textFaint },
  flags: { ...type.caption, color: colors.warn },
  sessionRow: {
    flexDirection: 'row',
    alignItems: 'center',
    gap: spacing.sm,
    padding: spacing.md,
  },
  sessionPages: { fontSize: 13, color: colors.text },
  sessionMeta: { ...type.caption, color: colors.textFaint, marginTop: 2 },
  sessionDuration: { fontSize: 12, color: colors.textMuted },
  reviewRow: { padding: spacing.lg, gap: spacing.sm },
  reviewHead: { flexDirection: 'row', alignItems: 'center', gap: spacing.sm },
  reviewAuthor: { ...type.label, color: colors.ink },
  rating: { fontSize: 12, color: colors.textMuted },
  reviewBody: { ...type.body, color: colors.text, lineHeight: 21 },
  emptyReview: { ...type.caption, color: colors.textMuted, lineHeight: 18 },
});
