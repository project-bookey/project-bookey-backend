import { useQuery } from '@tanstack/react-query';
import { useLocalSearchParams } from 'expo-router';
import { ScrollView, StyleSheet, Text, View } from 'react-native';

import { clubApi } from '@/api/endpoints';
import { BookCover } from '@/components/BookCover';
import {
  Card, Eyebrow, KeyValue, Loading, Numeral, ProgressBar, Rule, Screen, formatDuration, percent,
} from '@/components/ui';
import { colors, spacing, type, layout } from '@/theme';

/** 모임 결산 (§12.5) */
export default function ClubResultScreen() {
  const { id } = useLocalSearchParams<{ id: string }>();
  const clubId = Number(id);
  const result = useQuery({
    queryKey: ['club', clubId, 'result'],
    queryFn: () => clubApi.result(clubId),
    enabled: Number.isFinite(clubId),
  });

  if (result.isLoading) {
    return <Screen><Loading /></Screen>;
  }
  const data = result.data;
  if (!data) {
    return <Screen><Text style={styles.error}>결산을 불러오지 못했습니다.</Text></Screen>;
  }

  return (
    <Screen>
      <ScrollView contentContainerStyle={styles.container}>
        <View style={styles.header}>
          <BookCover url={data.book?.coverUrl} title={data.book?.title} width={56} />
          <View style={{ flex: 1 }}>
            <Eyebrow>모임 결산</Eyebrow>
            <Text style={styles.title}>{data.name}</Text>
            <Text style={styles.book}>{data.book?.title}</Text>
          </View>
        </View>

        <Card>
          <View style={styles.bigStat}>
            <Numeral style={styles.bigNumber}>{Math.round(data.finishRate * 100)}%</Numeral>
            <Text style={styles.bigLabel}>완독률</Text>
          </View>
          <ProgressBar value={data.finishRate} height={6} />
          <Rule />
          <KeyValue label="참여 인원" value={`${data.memberCount}명`} />
          <KeyValue label="완독" value={`${data.finishedCount}명`} />
          <KeyValue label="총 독서시간" value={formatDuration(data.totalDurationSec)} />
          {data.topDiscussant ? (
            <KeyValue label="최다 토론" value={data.topDiscussant} />
          ) : null}
        </Card>

        <View>
          <Eyebrow>최종 진행률</Eyebrow>
          <Card style={{ marginTop: spacing.sm, gap: spacing.md }}>
            {data.members.map((member) => (
              <View key={member.clubMemberId} style={{ gap: 5 }}>
                <View style={styles.memberRow}>
                  <Text style={styles.memberName}>{member.nickname}</Text>
                  <Numeral style={styles.memberValue}>
                    {member.shareProgress ? percent(member.completionRate) : '비공개'}
                  </Numeral>
                </View>
                <ProgressBar value={member.completionRate} height={4} />
              </View>
            ))}
          </Card>
        </View>

        {data.bestQuotes.length > 0 ? (
          <View>
            <Eyebrow>베스트 인용</Eyebrow>
            <Card style={{ marginTop: spacing.sm, gap: spacing.md }}>
              {data.bestQuotes.map((quote, index) => (
                <View key={index} style={styles.quote}>
                  <View style={styles.quoteBar} />
                  <Text style={styles.quoteText}>{quote}</Text>
                </View>
              ))}
            </Card>
          </View>
        ) : null}
      </ScrollView>
    </Screen>
  );
}

const styles = StyleSheet.create({
  container: { ...layout.content, padding: spacing.lg, gap: spacing.xl, paddingBottom: spacing.xxl },
  header: { flexDirection: 'row', gap: spacing.md, alignItems: 'center' },
  title: { ...type.title, color: colors.ink, marginTop: 4 },
  book: { ...type.caption, color: colors.textMuted },
  bigStat: { flexDirection: 'row', alignItems: 'baseline', gap: spacing.sm, marginBottom: spacing.md },
  bigNumber: { fontSize: 44, fontWeight: '800', color: colors.ink, letterSpacing: -2 },
  bigLabel: { ...type.label, color: colors.textMuted },
  memberRow: { flexDirection: 'row', justifyContent: 'space-between', alignItems: 'baseline' },
  memberName: { ...type.label, color: colors.text },
  memberValue: { fontSize: 12, color: colors.textMuted },
  quote: { flexDirection: 'row', gap: spacing.md },
  quoteBar: { width: 2, backgroundColor: colors.ink },
  quoteText: { ...type.body, color: colors.text, flex: 1, lineHeight: 21 },
  error: { ...type.body, color: colors.danger, padding: spacing.lg },
});
