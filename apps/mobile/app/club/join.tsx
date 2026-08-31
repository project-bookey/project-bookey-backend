import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { useRouter } from 'expo-router';
import { useState } from 'react';
import { ScrollView, StyleSheet, Text, TextInput, View } from 'react-native';

import { clubApi } from '@/api/endpoints';
import { ApiError } from '@/api/client';
import { BookCover } from '@/components/BookCover';
import { Button, Card, Eyebrow, KeyValue, Rule, Screen, Toggle } from '@/components/ui';
import { colors, fonts, hairline, spacing, type } from '@/theme';

/**
 * 코드로 참가 (§12.1).
 * 코드로 볼 수 있는 정보는 미리보기 수준까지다 — 멤버 진척·토론은 참가 후에만 보인다.
 */
export default function ClubJoinScreen() {
  const router = useRouter();
  const queryClient = useQueryClient();
  const [code, setCode] = useState('');
  const [shareProgress, setShareProgress] = useState(true);
  const [adoptTarget, setAdoptTarget] = useState(true);
  const [error, setError] = useState<string | null>(null);

  const normalized = code.replace(/[^A-Za-z0-9]/g, '').toUpperCase();
  const ready = normalized.length === 6;

  const preview = useQuery({
    queryKey: ['club', 'preview', normalized],
    queryFn: () => clubApi.preview(normalized),
    enabled: ready,
    retry: false,
  });

  const join = useMutation({
    mutationFn: () => clubApi.join(normalized, { adoptTargetDate: adoptTarget, shareProgress }),
    onSuccess: (club) => {
      queryClient.invalidateQueries({ queryKey: ['clubs'] });
      queryClient.invalidateQueries({ queryKey: ['library'] });
      router.replace(`/club/${club.id}`);
    },
    onError: (e) => setError(e instanceof ApiError ? e.message : '참가하지 못했습니다.'),
  });

  return (
    <Screen>
      <ScrollView contentContainerStyle={styles.container}>
        <View>
          <Eyebrow>초대 코드</Eyebrow>
          <TextInput
            value={code}
            onChangeText={(text) => {
              setCode(text.toUpperCase());
              setError(null);
            }}
            placeholder="ABC123"
            placeholderTextColor={colors.textFaint}
            autoCapitalize="characters"
            autoCorrect={false}
            maxLength={8}
            style={styles.codeInput}
          />
          <Text style={styles.hint}>대소문자와 하이픈은 자동으로 정리됩니다.</Text>
        </View>

        {ready && preview.isError ? (
          <Text style={styles.error}>유효하지 않은 초대 코드입니다.</Text>
        ) : null}

        {preview.data ? (
          <Card style={{ gap: spacing.md }}>
            <View style={styles.previewHead}>
              <BookCover
                url={preview.data.book?.coverUrl}
                title={preview.data.book?.title}
                width={52}
              />
              <View style={{ flex: 1, gap: 3 }}>
                <Text style={styles.clubName}>{preview.data.name}</Text>
                <Text style={styles.clubBook}>{preview.data.book?.title}</Text>
                {preview.data.description ? (
                  <Text numberOfLines={2} style={styles.clubDesc}>{preview.data.description}</Text>
                ) : null}
              </View>
            </View>

            <Rule />
            <KeyValue label="호스트" value={preview.data.hostNickname ?? '—'} />
            <KeyValue
              label="인원"
              value={`${preview.data.memberCount} / ${preview.data.memberLimit}`}
            />
            <KeyValue
              label="기간"
              value={`${preview.data.startsAt} → ${preview.data.endsAt}`}
            />

            {preview.data.alreadyMember ? (
              <Text style={styles.notice}>이미 참가 중인 모임입니다.</Text>
            ) : preview.data.joinBlockedReason ? (
              <Text style={styles.error}>{preview.data.joinBlockedReason}</Text>
            ) : null}
          </Card>
        ) : null}

        {preview.data?.joinable ? (
          <Card style={{ gap: spacing.md }}>
            <Eyebrow>참가하면 이렇게 됩니다</Eyebrow>
            <Text style={styles.consentText}>
              · 이 책이 내 서재에 자동으로 등록됩니다{'\n'}
              · 내 <Text style={styles.bold}>진행률 · 누적 독서시간 · 마지막 독서 시각</Text>이 모임원에게 보입니다{'\n'}
              · 세션 메모, 다른 책의 기록, 개인 독후감은 <Text style={styles.bold}>공유되지 않습니다</Text>
            </Text>

            <Rule />
            <Toggle
              label="진척 공개"
              description="끄면 리더보드에 '비공개'로 표시되고 모임 평균 계산에서 빠집니다."
              value={shareProgress}
              onChange={setShareProgress}
            />
            <Toggle
              label="모임 목표일을 내 목표로"
              description={`${preview.data.endsAt}을 내 완독 목표일로 삼습니다.`}
              value={adoptTarget}
              onChange={setAdoptTarget}
            />
          </Card>
        ) : null}

        {error ? <Text style={styles.error}>{error}</Text> : null}

        <Button
          label="참가하기"
          disabled={!preview.data?.joinable}
          loading={join.isPending}
          onPress={() => join.mutate()}
        />
      </ScrollView>
    </Screen>
  );
}

const styles = StyleSheet.create({
  container: {
    padding: spacing.lg,
    gap: spacing.lg,
    maxWidth: 520,
    width: '100%',
    alignSelf: 'center',
  },
  codeInput: {
    borderWidth: hairline,
    borderColor: colors.lineStrong,
    borderRadius: 12,
    backgroundColor: colors.surface,
    fontFamily: fonts.mono,
    fontSize: 30,
    fontWeight: '700',
    letterSpacing: 8,
    textAlign: 'center',
    paddingVertical: spacing.lg,
    color: colors.ink,
    marginTop: spacing.sm,
  },
  hint: { ...type.caption, color: colors.textFaint, marginTop: spacing.sm },
  previewHead: { flexDirection: 'row', gap: spacing.md },
  clubName: { ...type.title, color: colors.ink },
  clubBook: { ...type.caption, color: colors.textMuted },
  clubDesc: { ...type.caption, color: colors.textFaint, marginTop: 2, lineHeight: 16 },
  consentText: { ...type.body, color: colors.textMuted, lineHeight: 22 },
  bold: { fontWeight: '700', color: colors.ink },
  notice: { ...type.caption, color: colors.accent },
  error: { ...type.caption, color: colors.danger },
});
