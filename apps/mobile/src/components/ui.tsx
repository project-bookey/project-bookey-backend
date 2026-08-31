import { ReactNode } from 'react';
import {
  ActivityIndicator, Pressable, StyleSheet, Text, TextInput, TextInputProps, View, ViewStyle,
} from 'react-native';

import { colors, elevation, fonts, hairline, ornament, radius, spacing, type } from '@/theme';

export function Screen({ children, style }: { children: ReactNode; style?: ViewStyle }) {
  return <View style={[styles.screen, style]}>{children}</View>;
}

/** 각진 패널. 그림자 대신 얇은 선으로만 면을 나눈다. */
export function Card({ children, style }: { children: ReactNode; style?: ViewStyle }) {
  return <View style={[styles.card, style]}>{children}</View>;
}

/** 표제. 필사본의 단락 표시(❧)를 앞에 둔다. */
export function Eyebrow({ children, plain }: { children: ReactNode; plain?: boolean }) {
  return (
    <Text style={styles.eyebrow}>
      {plain ? null : <Text style={styles.eyebrowMark}>{ornament.section} </Text>}
      {children}
    </Text>
  );
}

export function SectionHeader({ title, action }: { title: string; action?: ReactNode }) {
  return (
    <View style={styles.sectionHeader}>
      <Eyebrow>{title}</Eyebrow>
      {action}
    </View>
  );
}

/** 겹괘선 — 장 구분에 쓰는 두 줄. */
export function DoubleRule() {
  return (
    <View style={styles.doubleRule}>
      <View style={styles.doubleRuleThick} />
      <View style={styles.doubleRuleThin} />
    </View>
  );
}

/** 장식 구분자 — 가운데 기호를 둔 괘선. */
export function OrnamentDivider() {
  return (
    <View style={styles.ornamentRow}>
      <View style={styles.ornamentLine} />
      <Text style={styles.ornamentMark}>{ornament.divider}</Text>
      <View style={styles.ornamentLine} />
    </View>
  );
}

export function Button({
  label, onPress, variant = 'primary', disabled, loading, style, size = 'md',
}: {
  label: string;
  onPress?: () => void;
  variant?: 'primary' | 'outline' | 'ghost' | 'danger';
  disabled?: boolean;
  loading?: boolean;
  style?: ViewStyle;
  size?: 'sm' | 'md';
}) {
  const isDisabled = disabled || loading;
  return (
    <Pressable
      accessibilityRole="button"
      onPress={onPress}
      disabled={isDisabled}
      style={({ pressed }) => [
        styles.button,
        size === 'sm' && styles.buttonSm,
        variant === 'primary' && styles.buttonPrimary,
        variant === 'outline' && styles.buttonOutline,
        variant === 'ghost' && styles.buttonGhost,
        variant === 'danger' && styles.buttonDanger,
        pressed && !isDisabled && styles.buttonPressed,
        isDisabled && styles.buttonDisabled,
        style,
      ]}
    >
      {loading ? (
        <ActivityIndicator size="small" color={variant === 'primary' ? '#FFFFFF' : colors.ink} />
      ) : (
        <Text
          style={[
            styles.buttonLabel,
            size === 'sm' && styles.buttonLabelSm,
            variant === 'primary' && styles.buttonLabelPrimary,
            variant === 'danger' && styles.buttonLabelDanger,
          ]}
        >
          {label}
        </Text>
      )}
    </Pressable>
  );
}

export function Tag({ label, fg, bg }: { label: string; fg?: string; bg?: string }) {
  return (
    <View style={[styles.tag, bg ? { backgroundColor: bg } : null]}>
      <Text style={[styles.tagText, fg ? { color: fg } : null]}>{label}</Text>
    </View>
  );
}

/** 각진 진행바. 채움과 트랙 사이 경계를 선명하게 둔다. */
export function ProgressBar({ value, height = 6 }: { value?: number | null; height?: number }) {
  const clamped = Math.max(0, Math.min(1, value ?? 0));
  return (
    <View style={[styles.track, { height }]}>
      <View style={[styles.fill, { width: `${clamped * 100}%` }]} />
    </View>
  );
}

export function Numeral({ children, style }: { children: ReactNode; style?: object }) {
  return <Text style={[styles.numeral, style]}>{children}</Text>;
}

export function Field({ label, hint, error, ...props }: TextInputProps & {
  label: string;
  hint?: string;
  error?: string | null;
}) {
  return (
    <View style={styles.field}>
      <Text style={styles.fieldLabel}>{label}</Text>
      <TextInput
        placeholderTextColor={colors.textFaint}
        style={[styles.input, error ? styles.inputError : null]}
        {...props}
      />
      {error ? <Text style={styles.fieldError}>{error}</Text> : null}
      {hint && !error ? <Text style={styles.fieldHint}>{hint}</Text> : null}
    </View>
  );
}

export function Segmented<T extends string>({ options, value, onChange }: {
  options: { value: T; label: string }[];
  value: T;
  onChange: (value: T) => void;
}) {
  return (
    <View style={styles.segmented}>
      {options.map((option, index) => {
        const active = option.value === value;
        return (
          <Pressable
            key={option.value}
            onPress={() => onChange(option.value)}
            style={[
              styles.segment,
              index > 0 && styles.segmentDivider,
              active && styles.segmentActive,
            ]}
          >
            <Text style={[styles.segmentLabel, active && styles.segmentLabelActive]}>
              {option.label}
            </Text>
          </Pressable>
        );
      })}
    </View>
  );
}

/**
 * 각진 토글. 플랫폼 기본 Switch 는 iOS/안드로이드/웹에서 색이 제각각이라
 * 디자인을 지키기 위해 직접 그린다.
 */
export function Toggle({ value, onChange, label, description }: {
  value: boolean;
  onChange: (value: boolean) => void;
  label?: string;
  description?: string;
}) {
  const control = (
    <Pressable
      accessibilityRole="switch"
      accessibilityState={{ checked: value }}
      onPress={() => onChange(!value)}
      style={[styles.toggleTrack, value && styles.toggleTrackOn]}
    >
      <View style={[styles.toggleKnob, value && styles.toggleKnobOn]} />
    </Pressable>
  );

  if (!label) {
    return control;
  }
  return (
    <View style={styles.toggleRow}>
      <View style={{ flex: 1, gap: 2 }}>
        <Text style={styles.toggleLabel}>{label}</Text>
        {description ? <Text style={styles.toggleDescription}>{description}</Text> : null}
      </View>
      {control}
    </View>
  );
}

export function EmptyState({ title, description, action }: {
  title: string;
  description?: string;
  action?: ReactNode;
}) {
  return (
    <View style={styles.empty}>
      <View style={styles.emptyRule} />
      <Text style={styles.emptyTitle}>{title}</Text>
      {description ? <Text style={styles.emptyDescription}>{description}</Text> : null}
      {action ? <View style={{ marginTop: spacing.lg }}>{action}</View> : null}
    </View>
  );
}

export function Loading() {
  return (
    <View style={styles.loading}>
      <ActivityIndicator size="small" color={colors.ink} />
    </View>
  );
}

export function Rule({ style }: { style?: ViewStyle }) {
  return <View style={[styles.rule, style]} />;
}

export function KeyValue({ label, value }: { label: string; value: ReactNode }) {
  return (
    <View style={styles.keyValue}>
      <Text style={styles.keyValueLabel}>{label}</Text>
      <Text style={styles.keyValueValue}>{value}</Text>
    </View>
  );
}

export function formatDuration(seconds?: number | null): string {
  const total = Math.max(0, Math.floor(seconds ?? 0));
  const hours = Math.floor(total / 3600);
  const minutes = Math.floor((total % 3600) / 60);
  if (hours > 0) return `${hours}시간 ${minutes}분`;
  if (minutes > 0) return `${minutes}분`;
  return `${total}초`;
}

export function formatClock(seconds: number): string {
  const total = Math.max(0, Math.floor(seconds));
  const h = Math.floor(total / 3600);
  const m = Math.floor((total % 3600) / 60);
  const s = total % 60;
  const pad = (n: number) => String(n).padStart(2, '0');
  return h > 0 ? `${pad(h)}:${pad(m)}:${pad(s)}` : `${pad(m)}:${pad(s)}`;
}

export function formatRelative(iso?: string | null): string {
  if (!iso) return '기록 없음';
  const minutes = Math.floor((Date.now() - new Date(iso).getTime()) / 60000);
  if (minutes < 1) return '방금';
  if (minutes < 60) return `${minutes}분 전`;
  const hours = Math.floor(minutes / 60);
  if (hours < 24) return `${hours}시간 전`;
  const days = Math.floor(hours / 24);
  if (days < 30) return `${days}일 전`;
  return new Date(iso).toLocaleDateString('ko-KR');
}

export function percent(value?: number | null): string {
  if (value === null || value === undefined) return '—';
  return `${Math.round(value * 100)}%`;
}

const styles = StyleSheet.create({
  screen: { flex: 1, backgroundColor: colors.bg },
  card: {
    backgroundColor: colors.surface,
    borderWidth: hairline,
    borderColor: colors.line,
    borderRadius: radius.lg,
    padding: spacing.lg,
    overflow: 'hidden',
    ...elevation.card,
  },
  eyebrow: { ...type.eyebrow, color: colors.textMuted },
  eyebrowMark: { color: colors.accent, fontSize: 11 },
  doubleRule: { gap: 2 },
  doubleRuleThick: { height: 2, backgroundColor: colors.ink },
  doubleRuleThin: { height: hairline, backgroundColor: colors.ink },
  ornamentRow: { flexDirection: 'row', alignItems: 'center', gap: spacing.sm },
  ornamentLine: { flex: 1, height: hairline, backgroundColor: colors.line },
  ornamentMark: { fontFamily: fonts.mono, fontSize: 12, color: colors.textFaint },
  sectionHeader: {
    flexDirection: 'row',
    alignItems: 'center',
    justifyContent: 'space-between',
    marginBottom: spacing.md,
  },
  button: {
    minHeight: 46,
    borderRadius: radius.md,
    alignItems: 'center',
    justifyContent: 'center',
    paddingHorizontal: spacing.lg,
  },
  buttonSm: { minHeight: 34, paddingHorizontal: spacing.md },
  buttonPrimary: { backgroundColor: colors.ink },
  buttonOutline: {
    backgroundColor: 'transparent',
    borderWidth: hairline,
    borderColor: colors.lineStrong,
  },
  buttonGhost: { backgroundColor: 'transparent', minHeight: 32, paddingHorizontal: 0 },
  buttonDanger: {
    backgroundColor: 'transparent',
    borderWidth: hairline,
    borderColor: colors.danger,
  },
  buttonPressed: { opacity: 0.7 },
  buttonDisabled: { opacity: 0.35 },
  buttonLabel: { ...type.label, color: colors.ink },
  buttonLabelSm: { fontSize: 11.5 },
  buttonLabelPrimary: { color: '#FFFFFF' },
  buttonLabelDanger: { color: colors.danger },
  tag: {
    paddingHorizontal: 7,
    paddingVertical: 3,
    borderRadius: radius.sm,
    backgroundColor: colors.surfaceAlt,
    alignSelf: 'flex-start',
  },
  tagText: {
    fontFamily: fonts.mono,
    fontSize: 10.5,
    fontWeight: '700',
    letterSpacing: 0.4,
    color: colors.textMuted,
  },
  track: {
    backgroundColor: colors.trackEmpty,
    width: '100%',
    overflow: 'hidden',
    borderRadius: radius.pill,
  },
  fill: { backgroundColor: colors.ink, height: '100%', borderRadius: radius.pill },
  numeral: { fontFamily: fonts.mono, fontSize: 13, fontWeight: '700', color: colors.text },
  field: { marginBottom: spacing.lg },
  fieldLabel: { ...type.eyebrow, color: colors.textMuted, marginBottom: spacing.sm },
  fieldHint: { ...type.caption, color: colors.textFaint, marginTop: spacing.xs },
  fieldError: { ...type.caption, color: colors.danger, marginTop: spacing.xs },
  input: {
    backgroundColor: colors.surface,
    borderWidth: hairline,
    borderColor: colors.line,
    borderRadius: radius.md,
    paddingHorizontal: spacing.md,
    paddingVertical: spacing.md,
    fontFamily: fonts.serif,
    fontSize: 15,
    color: colors.text,
  },
  inputError: { borderColor: colors.danger },
  segmented: {
    flexDirection: 'row',
    borderWidth: hairline,
    borderColor: colors.line,
    backgroundColor: colors.surface,
    borderRadius: radius.md,
    overflow: 'hidden',
  },
  segment: { flex: 1, paddingVertical: spacing.sm + 2, alignItems: 'center' },
  segmentDivider: { borderLeftWidth: hairline, borderLeftColor: colors.line },
  segmentActive: { backgroundColor: colors.ink },
  segmentLabel: { ...type.label, fontSize: 12, color: colors.textMuted },
  segmentLabelActive: { color: '#FFFFFF' },
  toggleTrack: {
    width: 46,
    height: 26,
    borderWidth: hairline,
    borderColor: colors.line,
    backgroundColor: colors.surfaceAlt,
    borderRadius: radius.pill,
    padding: 2,
    justifyContent: 'center',
  },
  toggleTrackOn: { backgroundColor: colors.ink, borderColor: colors.ink },
  toggleKnob: {
    width: 20,
    height: 20,
    borderRadius: radius.pill,
    backgroundColor: colors.textFaint,
  },
  toggleKnobOn: { backgroundColor: '#FFFFFF', alignSelf: 'flex-end' },
  toggleRow: { flexDirection: 'row', alignItems: 'center', gap: spacing.md },
  toggleLabel: { ...type.label, color: colors.ink },
  toggleDescription: { ...type.caption, color: colors.textFaint, lineHeight: 16 },
  empty: { alignItems: 'center', paddingVertical: spacing.xxl, paddingHorizontal: spacing.xl },
  emptyRule: { width: 28, height: 2, backgroundColor: colors.ink, marginBottom: spacing.lg },
  toggleTrackSpacer: { width: 0 },
  emptyTitle: { ...type.subtitle, color: colors.text, textAlign: 'center' },
  emptyDescription: {
    ...type.body,
    color: colors.textMuted,
    textAlign: 'center',
    marginTop: spacing.sm,
    lineHeight: 21,
  },
  loading: { paddingVertical: spacing.xxl, alignItems: 'center' },
  rule: { height: hairline, backgroundColor: colors.line },
  keyValue: {
    flexDirection: 'row',
    justifyContent: 'space-between',
    alignItems: 'baseline',
    paddingVertical: spacing.sm,
  },
  keyValueLabel: { ...type.caption, color: colors.textMuted },
  keyValueValue: { fontFamily: fonts.mono, fontSize: 13, fontWeight: '700', color: colors.text },
});
