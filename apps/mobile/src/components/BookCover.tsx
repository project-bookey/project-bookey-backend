import { Image, StyleSheet, Text, View } from 'react-native';

import { colors, hairline, radius } from '@/theme';

/** 표지가 없으면 제목 기반 대체 커버를 만든다 (§F8 품질 기준). */
export function BookCover({ url, title, width = 52 }: {
  url?: string | null;
  title?: string;
  width?: number;
}) {
  const height = Math.round(width * 1.44);

  if (url) {
    return <Image source={{ uri: url }} style={[styles.cover, { width, height }]} />;
  }
  return (
    <View style={[styles.cover, styles.fallback, { width, height }]}>
      <View style={styles.fallbackRule} />
      <Text numberOfLines={3} style={[styles.fallbackText, { fontSize: Math.max(8, width / 6.5) }]}>
        {title ?? '표지 없음'}
      </Text>
    </View>
  );
}

const styles = StyleSheet.create({
  cover: {
    borderRadius: radius.sm,
    backgroundColor: colors.surfaceAlt,
    borderWidth: hairline,
    borderColor: colors.line,
  },
  fallback: { alignItems: 'center', justifyContent: 'center', padding: 6, gap: 6 },
  fallbackRule: { width: 14, height: 1.5, backgroundColor: colors.textFaint },
  fallbackText: { color: colors.textMuted, textAlign: 'center', fontWeight: '600', lineHeight: 13 },
});
