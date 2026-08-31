import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { useLocalSearchParams, useRouter } from 'expo-router';
import { useState } from 'react';
import { Alert, Platform, Pressable, ScrollView, StyleSheet, Text, View } from 'react-native';

import { ApiError } from '@/api/client';
import { clubApi } from '@/api/endpoints';
import type { Checkpoint, ClubHome, MemberProgress, NudgeMessageKey } from '@/api/types';
import { BookCover } from '@/components/BookCover';
import {
  Button, Card, Eyebrow, Loading, Numeral, ProgressBar, Rule, Screen, Tag,
  formatDuration, formatRelative, percent,
} from '@/components/ui';
import { colors, fonts, hairline, paceStyle, spacing, type, layout } from '@/theme';

const NUDGES: { key: NudgeMessageKey; label: string }[] = [
  { key: 'READ_TOGETHER', label: '같이 읽어요' },
  { key: 'CHECKPOINT_SOON', label: '체크포인트 임박' },
  { key: 'WAITING', label: '기다리고 있어요' },
];

/** 모임 홈 (§12.2) — 멤버 진척 리스트 · 체크포인트 그리드 */
export default function ClubHomeScreen() {
  const router = useRouter();
  const queryClient = useQueryClient();
  const { id } = useLocalSearchParams<{ id: string }>();
  const clubId = Number(id);
  const [nudgeTarget, setNudgeTarget] = useState<MemberProgress | null>(null);

  const club = useQuery({
    queryKey: ['club', clubId],
    queryFn: () => clubApi.home(clubId),
    enabled: Number.isFinite(clubId),
  });

  const nudge = useMutation({
    mutationFn: ({ userId, key }: { userId: number; key: NudgeMessageKey }) =>
      clubApi.nudge(clubId, userId, key),
    onSuccess: (result) => {
      setNudgeTarget(null);
      notify(`찌르기를 보냈어요. 오늘 ${result.remainingToday}번 남았습니다.`);
    },
    onError: (e) => {
      setNudgeTarget(null);
      notify(e instanceof ApiError ? e.message : '보내지 못했습니다.');
    },
  });

  if (club.isLoading) {
    return <Screen><Loading /></Screen>;
  }
  if (!club.data) {
    return <Screen><Text style={styles.error}>모임을 불러오지 못했습니다.</Text></Screen>;
  }

  const data: ClubHome = club.data;
  const ended = data.status === 'ENDED' || data.status === 'ARCHIVED';

  return (
    <Screen>
      <ScrollView
        contentContainerStyle={styles.container}
        refreshControl={undefined}
      >
        <View style={styles.header}>
          <BookCover url={data.book?.coverUrl} title={data.book?.title} width={58} />
          <View style={{ flex: 1, gap: 4 }}>
            <Text style={styles.title}>{data.name}</Text>
            <Text style={styles.book}>{data.book?.title}</Text>
            <View style={styles.headerTags}>
              <Tag label={data.myRole === 'HOST' ? '호스트' : '멤버'} />
              <Tag label={`${data.memberCount}/${data.memberLimit}명`} />
              {ended ? (
                <Tag label="종료" />
              ) : (
                <Tag label={`D-${Math.max(0, data.daysLeft)}`} />
              )}
            </View>
          </View>
        </View>

        <Card style={{ gap: spacing.md }}>
          <View style={styles.summaryRow}>
            <SummaryCell
              label="모임 평균"
              value={percent(data.averageCompletionRate)}
            />
            <View style={styles.vRule} />
            <SummaryCell
              label="내 순위"
              value={`${data.myRank} / ${data.memberCount}`}
            />
            <View style={styles.vRule} />
            <SummaryCell
              label="기간"
              value={`${compactDate(data.startsAt)}–${compactDate(data.endsAt)}`}
            />
          </View>
          <Rule />
          <View style={styles.codeRow}>
            <View>
              <Eyebrow>초대 코드</Eyebrow>
              <Text style={styles.code}>{data.joinCode}</Text>
            </View>
            <Button
              label="토론 열기"
              size="sm"
              variant="outline"
              onPress={() => router.push(`/club/${clubId}/posts`)}
            />
          </View>
        </Card>

        {data.nextCheckpoint ? (
          <View>
            <Eyebrow>다음 체크포인트</Eyebrow>
            <Card style={{ marginTop: spacing.sm, gap: spacing.sm }}>
              <View style={styles.checkpointHead}>
                <Text style={styles.checkpointTitle}>{data.nextCheckpoint.title}</Text>
                <Numeral style={styles.checkpointTarget}>
                  ~{data.nextCheckpoint.targetPage}쪽
                </Numeral>
              </View>
              <Text style={styles.checkpointMeta}>
                마감 {new Date(data.nextCheckpoint.dueAt).toLocaleDateString('ko-KR')} ·{' '}
                {data.nextCheckpoint.achievedCount}/{data.nextCheckpoint.memberCount}명 달성
              </Text>
            </Card>
          </View>
        ) : null}

        <View>
          <Eyebrow>멤버 진척</Eyebrow>
          <Card style={{ marginTop: spacing.sm, padding: 0 }}>
            {data.members.map((member, index) => (
              <View key={member.clubMemberId}>
                {index > 0 ? <Rule /> : null}
                <MemberRow
                  member={member}
                  rank={index + 1}
                  onNudge={() => setNudgeTarget(member)}
                />
              </View>
            ))}
          </Card>
        </View>

        {data.checkpoints.length > 0 ? (
          <View>
            <Eyebrow>체크포인트 진행</Eyebrow>
            <CheckpointGrid checkpoints={data.checkpoints} />
          </View>
        ) : null}

        {nudgeTarget ? (
          <Card style={{ gap: spacing.sm }}>
            <Eyebrow>{nudgeTarget.nickname}님에게 보내기</Eyebrow>
            <Text style={styles.nudgeHint}>
              프리셋 문구만 보낼 수 있습니다. 같은 사람에게 24시간에 한 번.
            </Text>
            <View style={styles.nudgeButtons}>
              {NUDGES.map((item) => (
                <Button
                  key={item.key}
                  label={item.label}
                  size="sm"
                  variant="outline"
                  style={{ flexGrow: 1 }}
                  loading={nudge.isPending}
                  onPress={() => nudge.mutate({ userId: nudgeTarget.userId, key: item.key })}
                />
              ))}
            </View>
            <Button label="취소" variant="ghost" size="sm" onPress={() => setNudgeTarget(null)} />
          </Card>
        ) : null}

        {ended ? (
          <Button
            label="모임 결산 보기"
            variant="outline"
            onPress={() => router.push(`/club/${clubId}/result`)}
          />
        ) : null}

        <ClubFooterActions
          club={data}
          onLeft={() => {
            queryClient.invalidateQueries({ queryKey: ['clubs'] });
            router.replace('/(tabs)/clubs');
          }}
        />
      </ScrollView>
    </Screen>
  );
}

/** 08-31 → 8/31 */
function compactDate(iso: string): string {
  const [, month, day] = iso.split('-');
  return `${Number(month)}/${Number(day)}`;
}

function SummaryCell({ label, value }: { label: string; value: string }) {
  return (
    <View style={styles.summaryCell}>
      <Text style={styles.summaryLabel}>{label}</Text>
      <Numeral style={styles.summaryValue}>{value}</Numeral>
    </View>
  );
}

function MemberRow({ member, rank, onNudge }: {
  member: MemberProgress;
  rank: number;
  onNudge: () => void;
}) {
  const pace = member.paceStatus ? paceStyle[member.paceStatus] : null;

  return (
    <View style={[styles.memberRow, member.isMe && styles.memberRowMe]}>
      <Numeral style={styles.rank}>{rank}</Numeral>
      <View style={{ flex: 1, gap: 4 }}>
        <View style={styles.memberHead}>
          <Text style={styles.memberName}>
            {member.nickname}
            {member.isMe ? ' (나)' : ''}
          </Text>
          {member.role === 'HOST' ? <Tag label="호스트" /> : null}
          {member.finished ? <Tag label="완독" fg={colors.accent} bg={colors.accentSoft} /> : null}
          {pace && !member.finished ? <Tag label={pace.label} fg={pace.fg} bg={pace.bg} /> : null}
        </View>

        {member.shareProgress ? (
          <>
            <ProgressBar value={member.completionRate} height={5} />
            <View style={styles.memberMeta}>
              <Numeral style={styles.memberNumeral}>
                {member.currentPage}쪽 · {percent(member.completionRate)} ·{' '}
                {formatDuration(member.totalDurationSec)}
              </Numeral>
              <Text style={styles.memberTime}>{formatRelative(member.lastReadAt)}</Text>
            </View>
          </>
        ) : (
          <Text style={styles.private}>진척 비공개</Text>
        )}
      </View>

      {member.nudgeable ? (
        <Pressable onPress={onNudge} style={styles.nudgeButton}>
          <Text style={styles.nudgeButtonText}>찌르기</Text>
        </Pressable>
      ) : null}
    </View>
  );
}

function CheckpointGrid({ checkpoints }: { checkpoints: Checkpoint[] }) {
  return (
    <View style={styles.grid}>
      {checkpoints.map((cp) => {
        const state = !cp.evaluated ? 'pending' : cp.myAchieved ? 'met' : 'missed';
        return (
          <View key={cp.id} style={styles.gridCell}>
            <View
              style={[
                styles.gridMark,
                state === 'met' && styles.gridMarkMet,
                state === 'missed' && styles.gridMarkMissed,
              ]}
            >
              <Text
                style={[
                  styles.gridMarkText,
                  state === 'met' && { color: '#FFFFFF' },
                  state === 'missed' && { color: colors.danger },
                ]}
              >
                {state === 'met' ? '✓' : state === 'missed' ? '×' : '·'}
              </Text>
            </View>
            <Text style={styles.gridLabel}>{cp.seq}주</Text>
            <Numeral style={styles.gridPage}>{cp.targetPage}</Numeral>
          </View>
        );
      })}
    </View>
  );
}

function ClubFooterActions({ club, onLeft }: { club: ClubHome; onLeft: () => void }) {
  const queryClient = useQueryClient();
  const leave = useMutation({
    mutationFn: () => clubApi.leave(club.id),
    onSuccess: onLeft,
    onError: (e) => notify(e instanceof ApiError ? e.message : '나가지 못했습니다.'),
  });
  const rotate = useMutation({
    mutationFn: () => clubApi.rotateCode(club.id),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['club', club.id] });
      notify('초대 코드를 새로 발급했습니다.');
    },
  });

  return (
    <View style={{ gap: spacing.sm }}>
      <Rule />
      {club.myRole === 'HOST' ? (
        <Button
          label="초대 코드 재발급"
          variant="ghost"
          size="sm"
          loading={rotate.isPending}
          onPress={() => rotate.mutate()}
        />
      ) : null}
      <Button
        label="모임 나가기"
        variant="ghost"
        size="sm"
        loading={leave.isPending}
        onPress={() => leave.mutate()}
      />
    </View>
  );
}

function notify(message: string) {
  if (Platform.OS === 'web') {
    // eslint-disable-next-line no-alert
    window.alert(message);
  } else {
    Alert.alert('', message);
  }
}

const styles = StyleSheet.create({
  container: { ...layout.content, padding: spacing.lg, gap: spacing.xl, paddingBottom: spacing.xxl },
  header: { flexDirection: 'row', gap: spacing.md },
  title: { ...type.title, color: colors.ink },
  book: { ...type.caption, color: colors.textMuted },
  headerTags: { flexDirection: 'row', gap: spacing.xs, marginTop: spacing.xs, flexWrap: 'wrap' },
  summaryRow: { flexDirection: 'row', alignItems: 'stretch' },
  summaryCell: { flex: 1, gap: 4 },
  summaryLabel: { ...type.caption, color: colors.textFaint },
  summaryValue: { fontSize: 14, fontWeight: '700', color: colors.ink },
  vRule: { width: hairline, backgroundColor: colors.line, marginHorizontal: spacing.md },
  codeRow: { flexDirection: 'row', alignItems: 'center', justifyContent: 'space-between' },
  code: {
    fontFamily: fonts.mono,
    fontSize: 22,
    fontWeight: '700',
    letterSpacing: 5,
    color: colors.ink,
  },
  checkpointHead: { flexDirection: 'row', justifyContent: 'space-between', alignItems: 'baseline' },
  checkpointTitle: { ...type.subtitle, color: colors.ink },
  checkpointTarget: { fontSize: 14, color: colors.accent, fontWeight: '700' },
  checkpointMeta: { ...type.caption, color: colors.textMuted },
  memberRow: { flexDirection: 'row', gap: spacing.md, padding: spacing.lg, alignItems: 'center' },
  memberRowMe: { backgroundColor: colors.surfaceAlt },
  rank: { fontSize: 12, color: colors.textFaint, width: 14 },
  memberHead: { flexDirection: 'row', alignItems: 'center', gap: spacing.xs, flexWrap: 'wrap' },
  memberName: { ...type.label, color: colors.ink },
  memberMeta: { flexDirection: 'row', justifyContent: 'space-between', alignItems: 'baseline' },
  memberNumeral: { fontSize: 11, color: colors.textMuted },
  memberTime: { ...type.caption, color: colors.textFaint },
  private: { ...type.caption, color: colors.textFaint },
  nudgeButton: {
    borderWidth: hairline,
    borderColor: colors.line,
    borderRadius: 999,
    paddingHorizontal: spacing.md,
    paddingVertical: 6,
  },
  nudgeButtonText: { fontSize: 11, fontWeight: '700', color: colors.textMuted },
  nudgeHint: { ...type.caption, color: colors.textFaint },
  nudgeButtons: { flexDirection: 'row', gap: spacing.sm, flexWrap: 'wrap' },
  grid: { flexDirection: 'row', gap: spacing.sm, marginTop: spacing.sm, flexWrap: 'wrap' },
  gridCell: { alignItems: 'center', gap: 4, width: 52 },
  gridMark: {
    width: 34,
    height: 34,
    borderRadius: 8,
    borderWidth: hairline,
    borderColor: colors.line,
    alignItems: 'center',
    justifyContent: 'center',
    backgroundColor: colors.surface,
  },
  gridMarkMet: { backgroundColor: colors.ink, borderColor: colors.ink },
  gridMarkMissed: { borderColor: colors.danger },
  gridMarkText: { fontSize: 14, fontWeight: '700', color: colors.textFaint },
  gridLabel: { ...type.caption, color: colors.textMuted },
  gridPage: { fontSize: 10, color: colors.textFaint },
  error: { ...type.body, color: colors.danger, padding: spacing.lg },
});
