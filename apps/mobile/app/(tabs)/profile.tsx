import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { useRouter } from 'expo-router';
import { Pressable, ScrollView, StyleSheet, Text, View } from 'react-native';

import { API_BASE_URL } from '@/api/client';
import { libraryApi, notificationApi } from '@/api/endpoints';
import type { NotifyTone } from '@/api/types';
import {
  Button, Card, Eyebrow, KeyValue, Numeral, Rule, Screen, Toggle,
} from '@/components/ui';
import { useAuth } from '@/store/auth';
import { colors, hairline, spacing, type, layout } from '@/theme';

const TONES: { value: NotifyTone; label: string; sample: string }[] = [
  { value: 'GENTLE', label: '다정', sample: '12쪽 남았어요. 오늘 10분이면 끝나요.' },
  { value: 'FACT', label: '팩트', sample: '5일 미독. 완독 예상일이 9/12 → 10/3으로 밀립니다.' },
  { value: 'SPARTA', label: '스파르타', sample: '5일째 안 읽음. 책이 당신을 노려보고 있습니다.' },
  { value: 'TSUNDERE', label: '츤데레', sample: '뭐, 안 읽어도 상관없는데. 남은 12쪽이 좀 불쌍하긴 하네.' },
  { value: 'SILENT', label: '무음', sample: '푸시 없이 인앱 배지로만 알립니다.' },
];

/** 탭 5. 프로필 — 알림 톤 · 설정 (§6) */
export default function ProfileScreen() {
  const router = useRouter();
  const queryClient = useQueryClient();
  const user = useAuth((s) => s.user);
  const setUser = useAuth((s) => s.setUser);
  const logout = useAuth((s) => s.logout);

  const summary = useQuery({ queryKey: ['library', 'summary'], queryFn: libraryApi.summary });

  const updateSettings = useMutation({
    mutationFn: (body: Record<string, unknown>) => notificationApi.updateSettings(body),
    onSuccess: (_, body) => {
      if (user) {
        setUser({ ...user, ...(body as object) } as typeof user);
      }
      queryClient.invalidateQueries({ queryKey: ['me'] });
    },
  });

  return (
    <Screen>
      <ScrollView contentContainerStyle={styles.container}>
        <View>
          <Text style={styles.nickname}>{user?.nickname}</Text>
          <Text style={styles.handle}>@{user?.handle}</Text>
        </View>

        <Card>
          <Eyebrow>내 서재</Eyebrow>
          <View style={styles.counts}>
            <CountCell label="읽는 중" value={summary.data?.reading ?? 0} />
            <CountCell label="완독" value={summary.data?.finished ?? 0} />
            <CountCell label="읽고 싶은" value={summary.data?.wantToRead ?? 0} />
            <CountCell label="하차" value={summary.data?.abandoned ?? 0} />
          </View>
        </Card>

        <View>
          <Eyebrow>재촉 톤</Eyebrow>
          <Text style={styles.helper}>
            같은 상황이라도 어떻게 말을 걸지 고를 수 있습니다.
          </Text>
          <View style={styles.toneList}>
            {TONES.map((tone) => {
              const selected = user?.notifyTone === tone.value;
              return (
                <Pressable
                  key={tone.value}
                  style={[styles.toneRow, selected && styles.toneRowSelected]}
                  onPress={() => updateSettings.mutate({ notifyTone: tone.value })}
                >
                  <View style={[styles.radio, selected && styles.radioOn]} />
                  <View style={{ flex: 1 }}>
                    <Text style={[styles.toneLabel, selected && styles.toneLabelSelected]}>
                      {tone.label}
                    </Text>
                    <Text style={styles.toneSample}>{tone.sample}</Text>
                  </View>
                </Pressable>
              );
            })}
          </View>
        </View>

        <Card>
          <Eyebrow>알림</Eyebrow>
          <View style={{ marginTop: spacing.sm }}>
            <KeyValue
              label="조용 시간"
              value={`${user?.quietHoursStart ?? 22}:00 – ${user?.quietHoursEnd ?? 8}:00`}
            />
            <Rule />
            <KeyValue label="하루 최대" value={`개인 ${user?.dailyNotifyCap ?? 2}건 · 모임 ${user?.clubNotifyCap ?? 3}건`} />
            <Rule />
            <View style={styles.switchRow}>
              <View style={{ flex: 1 }}>
                <Text style={styles.switchLabel}>찌르기 받기</Text>
                <Text style={styles.switchDesc}>
                  모임원이 프리셋 문구로 보내는 가벼운 재촉입니다.
                </Text>
              </View>
              <Toggle
                value={user?.allowNudge ?? true}
                onChange={(value) => updateSettings.mutate({ allowNudge: value })}
              />
            </View>
          </View>
        </Card>

        <View style={{ gap: spacing.sm }}>
          <Rule />
          <Text style={styles.meta}>API {API_BASE_URL}</Text>
          <Button
            label="로그아웃"
            variant="ghost"
            size="sm"
            onPress={async () => {
              await logout();
              router.replace('/login');
            }}
          />
        </View>
      </ScrollView>
    </Screen>
  );
}

function CountCell({ label, value }: { label: string; value: number }) {
  return (
    <View style={styles.countCell}>
      <Numeral style={styles.countValue}>{value}</Numeral>
      <Text style={styles.countLabel}>{label}</Text>
    </View>
  );
}

const styles = StyleSheet.create({
  container: { ...layout.content, padding: spacing.lg, gap: spacing.xl, paddingBottom: spacing.xxl },
  nickname: { ...type.display, color: colors.ink },
  handle: { ...type.caption, color: colors.textFaint, marginTop: 2 },
  counts: { flexDirection: 'row', marginTop: spacing.md },
  countCell: { flex: 1, gap: 3 },
  countValue: { fontSize: 20, fontWeight: '700', color: colors.ink },
  countLabel: { ...type.caption, color: colors.textFaint },
  helper: { ...type.caption, color: colors.textFaint, marginTop: spacing.sm },
  toneList: {
    marginTop: spacing.sm,
    borderWidth: hairline,
    borderColor: colors.line,
    borderRadius: 12,
    overflow: 'hidden',
  },
  toneRow: {
    flexDirection: 'row',
    gap: spacing.md,
    padding: spacing.md,
    alignItems: 'flex-start',
    borderBottomWidth: hairline,
    borderBottomColor: colors.line,
    backgroundColor: colors.surface,
  },
  toneRowSelected: { backgroundColor: colors.accentSoft },
  radio: {
    width: 16,
    height: 16,
    borderRadius: 999,
    borderWidth: hairline,
    borderColor: colors.textFaint,
    marginTop: 2,
  },
  radioOn: { backgroundColor: colors.ink, borderColor: colors.ink },
  toneLabel: { ...type.label, color: colors.text },
  toneLabelSelected: { color: colors.ink },
  toneSample: { ...type.caption, color: colors.textMuted, marginTop: 3, lineHeight: 16 },
  switchRow: {
    flexDirection: 'row',
    alignItems: 'center',
    gap: spacing.md,
    paddingVertical: spacing.sm,
  },
  switchLabel: { ...type.label, color: colors.text },
  switchDesc: { ...type.caption, color: colors.textFaint, marginTop: 2 },
  meta: { ...type.caption, color: colors.textFaint },
});
