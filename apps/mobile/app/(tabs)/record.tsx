import { useQuery } from '@tanstack/react-query';
import { ScrollView, StyleSheet, Text, View } from 'react-native';

import { statsApi } from '@/api/endpoints';
import {
  Card, Eyebrow, KeyValue, Loading, Numeral, Rule, Screen, formatDuration,
} from '@/components/ui';
import { colors, hairline, spacing, type, layout } from '@/theme';

/** 탭 4. 기록 — 캘린더 히트맵 · 통계 (§6, §F9) */
export default function RecordScreen() {
  const stats = useQuery({ queryKey: ['stats', 90], queryFn: () => statsApi.summary(90) });

  if (stats.isLoading) {
    return <Screen><Loading /></Screen>;
  }
  const data = stats.data;
  if (!data) {
    return <Screen><Text style={styles.error}>통계를 불러오지 못했습니다.</Text></Screen>;
  }

  const max = Math.max(1, ...data.daily.map((d) => d.durationSec));

  return (
    <Screen>
      <ScrollView contentContainerStyle={styles.container}>
        <View style={styles.statRow}>
          <StatCell label="현재 스트릭" value={`${data.currentStreakDays}일`} />
          <View style={styles.vRule} />
          <StatCell label="최장 스트릭" value={`${data.longestStreakDays}일`} />
          <View style={styles.vRule} />
          <StatCell label="이번 주" value={formatDuration(data.weekDurationSec)} />
        </View>

        <View>
          <Eyebrow>최근 90일</Eyebrow>
          <Card style={{ marginTop: spacing.sm }}>
            <Heatmap daily={data.daily} max={max} />
            <View style={styles.legend}>
              <Text style={styles.legendText}>적음</Text>
              {[0, 0.25, 0.5, 0.75, 1].map((level) => (
                <View key={level} style={[styles.legendCell, { opacity: level === 0 ? 1 : undefined, backgroundColor: cellColor(level) }]} />
              ))}
              <Text style={styles.legendText}>많음</Text>
            </View>
          </Card>
        </View>

        <Card>
          <Eyebrow>누적</Eyebrow>
          <View style={{ marginTop: spacing.sm }}>
            <KeyValue label="총 독서시간" value={formatDuration(data.totalDurationSec)} />
            <Rule />
            <KeyValue label="오늘" value={formatDuration(data.todayDurationSec)} />
            <Rule />
            <KeyValue
              label="기록한 날"
              value={`${data.daily.filter((d) => d.sessionCount > 0).length}일 / 90일`}
            />
          </View>
        </Card>
      </ScrollView>
    </Screen>
  );
}

function StatCell({ label, value }: { label: string; value: string }) {
  return (
    <View style={styles.statCell}>
      <Numeral style={styles.statValue}>{value}</Numeral>
      <Text style={styles.statLabel}>{label}</Text>
    </View>
  );
}

/** 주 단위 열로 쌓는 사각 히트맵. 둥근 점 대신 각진 칸을 쓴다. */
function Heatmap({ daily, max }: {
  daily: { date: string; durationSec: number }[];
  max: number;
}) {
  const weeks: { date: string; durationSec: number }[][] = [];
  let current: { date: string; durationSec: number }[] = [];

  daily.forEach((day, index) => {
    const weekday = new Date(day.date).getDay();
    if (index === 0) {
      for (let i = 0; i < weekday; i++) {
        current.push({ date: '', durationSec: -1 });
      }
    }
    current.push(day);
    if (current.length === 7) {
      weeks.push(current);
      current = [];
    }
  });
  if (current.length > 0) {
    weeks.push(current);
  }

  return (
    <View style={styles.heatmap}>
      {weeks.map((week, weekIndex) => (
        <View key={weekIndex} style={styles.heatWeek}>
          {week.map((day, dayIndex) => (
            <View
              key={`${weekIndex}-${dayIndex}`}
              style={[
                styles.heatCell,
                day.durationSec < 0
                  ? { backgroundColor: 'transparent' }
                  : { backgroundColor: cellColor(day.durationSec / max) },
              ]}
            />
          ))}
        </View>
      ))}
    </View>
  );
}

function cellColor(ratio: number): string {
  if (ratio <= 0) return colors.trackEmpty;
  if (ratio < 0.25) return '#BFCAD6';
  if (ratio < 0.5) return '#8095AC';
  if (ratio < 0.75) return '#456181';
  return colors.accent;
}

const styles = StyleSheet.create({
  container: { ...layout.content, padding: spacing.lg, gap: spacing.xl, paddingBottom: spacing.xxl },
  statRow: { flexDirection: 'row', alignItems: 'stretch' },
  statCell: { flex: 1, gap: 4 },
  statValue: { fontSize: 20, fontWeight: '700', color: colors.ink },
  statLabel: { ...type.caption, color: colors.textFaint },
  vRule: { width: hairline, backgroundColor: colors.line, marginHorizontal: spacing.md },
  heatmap: { flexDirection: 'row', gap: 3, flexWrap: 'wrap' },
  heatWeek: { gap: 3 },
  heatCell: { width: 11, height: 11 },
  legend: {
    flexDirection: 'row',
    alignItems: 'center',
    gap: 4,
    marginTop: spacing.md,
    justifyContent: 'flex-end',
  },
  legendCell: { width: 11, height: 11 },
  legendText: { ...type.caption, color: colors.textFaint },
  error: { ...type.body, color: colors.danger, padding: spacing.lg },
});
