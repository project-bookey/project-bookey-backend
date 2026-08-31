import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { useLocalSearchParams, useRouter } from 'expo-router';
import { useEffect, useRef, useState } from 'react';
import { AppState, Pressable, StyleSheet, Text, TextInput, View } from 'react-native';

import { libraryApi, sessionApi } from '@/api/endpoints';
import { BookCover } from '@/components/BookCover';
import {
  Button, Loading, Numeral, ProgressBar, Rule, Screen, formatClock, formatDuration, percent,
} from '@/components/ui';
import { colors, fonts, hairline, layout, spacing, type } from '@/theme';

/**
 * 독서 타이머 (§F3).
 *
 * 경과 시간은 매초 더하는 대신 <b>시작 시각과 현재 시각의 차</b>로 계산한다.
 * 앱이 백그라운드로 가거나 죽어도 복원되고, 클라이언트 시계 조작에도 서버 판정이 흔들리지 않는다.
 * 포그라운드 유지 비율과 상호작용 횟수를 함께 보내 어뷰징 판정에 쓴다(§8.3).
 */
export default function TimerScreen() {
  const router = useRouter();
  const queryClient = useQueryClient();
  const { recordId } = useLocalSearchParams<{ recordId: string }>();
  const id = Number(recordId);

  const record = useQuery({
    queryKey: ['library', 'record', id],
    queryFn: () => libraryApi.detail(id),
    enabled: Number.isFinite(id),
  });
  const current = useQuery({ queryKey: ['session', 'current'], queryFn: sessionApi.current });

  const [elapsed, setElapsed] = useState(0);
  const [endPage, setEndPage] = useState('');
  const [memo, setMemo] = useState('');

  const interactions = useRef(0);
  const foregroundMs = useRef(0);
  const totalMs = useRef(0);
  const lastTick = useRef(Date.now());
  const appActive = useRef(AppState.currentState === 'active');

  const session = current.data?.readingRecordId === id ? current.data : null;
  const startedAt = session ? new Date(session.startedAt).getTime() : null;

  useEffect(() => {
    const subscription = AppState.addEventListener('change', (state) => {
      appActive.current = state === 'active';
    });
    return () => subscription.remove();
  }, []);

  useEffect(() => {
    if (!startedAt) {
      setElapsed(0);
      return;
    }
    const tick = () => {
      const now = Date.now();
      const delta = now - lastTick.current;
      lastTick.current = now;
      totalMs.current += delta;
      if (appActive.current) {
        foregroundMs.current += delta;
      }
      setElapsed(Math.floor((now - startedAt) / 1000));
    };
    tick();
    const timer = setInterval(tick, 1000);
    return () => clearInterval(timer);
  }, [startedAt]);

  useEffect(() => {
    if (record.data && !endPage) {
      setEndPage(String(record.data.progress.currentPage));
    }
  }, [record.data]);

  const start = useMutation({
    mutationFn: () => sessionApi.start(id, record.data?.progress.currentPage),
    onSuccess: () => {
      lastTick.current = Date.now();
      foregroundMs.current = 0;
      totalMs.current = 0;
      interactions.current = 0;
      queryClient.invalidateQueries({ queryKey: ['session', 'current'] });
    },
  });

  const end = useMutation({
    mutationFn: () => {
      const ratio = totalMs.current > 0 ? foregroundMs.current / totalMs.current : 1;
      return sessionApi.end(session!.id, {
        endPage: endPage ? Number(endPage) : undefined,
        foregroundRatio: Math.min(1, Math.max(0, Number(ratio.toFixed(3)))),
        interactionCount: interactions.current,
        memo: memo.trim() || undefined,
      });
    },
    onSuccess: (result) => {
      queryClient.invalidateQueries({ queryKey: ['library'] });
      queryClient.invalidateQueries({ queryKey: ['session', 'current'] });
      queryClient.invalidateQueries({ queryKey: ['stats'] });
      queryClient.invalidateQueries({ queryKey: ['clubs'] });
      if (result.bookFinished) {
        router.replace(`/book/${record.data?.book?.id}?recordId=${id}&finished=1`);
      } else {
        router.back();
      }
    },
  });

  if (record.isLoading || current.isLoading) {
    return <Screen><Loading /></Screen>;
  }

  const progress = record.data?.progress;
  const running = Boolean(session);

  return (
    <Screen>
      <Pressable style={styles.container} onPress={() => { interactions.current += 1; }}>
        <View style={styles.bookRow}>
          <BookCover url={record.data?.book?.coverUrl} title={record.data?.book?.title} width={46} />
          <View style={{ flex: 1 }}>
            <Text numberOfLines={2} style={styles.bookTitle}>{record.data?.book?.title}</Text>
            <Text style={styles.bookMeta}>
              {progress?.currentPage}
              {progress && progress.totalPages > 0 ? ` / ${progress.totalPages}쪽` : '쪽'}
              {progress?.completionRate != null ? ` · ${percent(progress.completionRate)}` : ''}
            </Text>
          </View>
        </View>

        <View style={styles.clockBox}>
          <Text style={styles.clock}>{formatClock(elapsed)}</Text>
          <Text style={styles.clockLabel}>
            {running ? '기록 중' : '시작을 누르면 기록됩니다'}
          </Text>
        </View>

        <ProgressBar value={progress?.completionRate} height={4} />

        {running ? (
          <View style={styles.endForm}>
            <Rule />
            <Text style={styles.formLabel}>몇 쪽까지 읽었나요?</Text>
            <View style={styles.pageRow}>
              <TextInput
                value={endPage}
                onChangeText={(text) => {
                  interactions.current += 1;
                  setEndPage(text.replace(/[^0-9]/g, ''));
                }}
                keyboardType="number-pad"
                style={styles.pageInput}
                placeholder="0"
                placeholderTextColor={colors.textFaint}
              />
              <Text style={styles.pageSuffix}>
                {progress && progress.totalPages > 0 ? `/ ${progress.totalPages}쪽` : '쪽'}
              </Text>
            </View>
            <TextInput
              value={memo}
              onChangeText={setMemo}
              placeholder="이번 세션 메모 (선택)"
              placeholderTextColor={colors.textFaint}
              style={styles.memoInput}
              multiline
            />
            <Button
              label="세션 종료"
              onPress={() => end.mutate()}
              loading={end.isPending}
            />
          </View>
        ) : (
          <View style={styles.startArea}>
            <Button
              label="독서 시작"
              onPress={() => start.mutate()}
              loading={start.isPending}
            />
            <Text style={styles.hint}>
              누적 {formatDuration(progress?.totalDurationSec ?? 0)} 읽었습니다.
            </Text>
          </View>
        )}
      </Pressable>
    </Screen>
  );
}

const styles = StyleSheet.create({
  container: { ...layout.content, flex: 1, padding: spacing.lg, gap: spacing.xl },
  bookRow: { flexDirection: 'row', gap: spacing.md, alignItems: 'center' },
  bookTitle: { ...type.subtitle, color: colors.ink },
  bookMeta: { ...type.caption, color: colors.textMuted, marginTop: 3 },
  clockBox: { alignItems: 'center', paddingVertical: spacing.xl, gap: spacing.sm },
  clock: {
    fontFamily: fonts.mono,
    fontSize: 58,
    fontWeight: '700',
    color: colors.ink,
    letterSpacing: 2,
  },
  clockLabel: { ...type.caption, color: colors.textFaint, letterSpacing: 0.4 },
  startArea: { gap: spacing.md },
  hint: { ...type.caption, color: colors.textFaint, textAlign: 'center' },
  endForm: { gap: spacing.md },
  formLabel: { ...type.eyebrow, color: colors.textFaint },
  pageRow: { flexDirection: 'row', alignItems: 'baseline', gap: spacing.sm },
  pageInput: {
    flex: 1,
    borderBottomWidth: 2,
    borderBottomColor: colors.ink,
    minWidth: 0,
    fontFamily: fonts.mono,
    fontSize: 34,
    fontWeight: '700',
    color: colors.ink,
    paddingVertical: spacing.sm,
  },
  pageSuffix: { ...type.body, color: colors.textMuted, fontFamily: fonts.mono, flexShrink: 0 },
  memoInput: {
    borderWidth: hairline,
    borderColor: colors.line,
    borderRadius: 8,
    backgroundColor: colors.surface,
    padding: spacing.md,
    minHeight: 64,
    fontSize: 14,
    color: colors.text,
    textAlignVertical: 'top',
  },
});
