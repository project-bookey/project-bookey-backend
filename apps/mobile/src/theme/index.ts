import { Platform } from 'react-native';

/**
 * bookey 디자인 토큰.
 *
 * 방향: 필사본과 타자기. 본문은 세리프로 앉히고, 라벨·숫자는 타자기 등폭으로 찍는다.
 * 모서리는 각지게 두고 장식은 괘선과 필사 기호로만 만든다.
 */

export const fonts = {
  /** 본문 — 세리프 */
  serif: Platform.select({
    ios: 'Palatino',
    android: 'serif',
    default: '"Iowan Old Style", Palatino, "Palatino Linotype", Georgia, serif',
  }) as string,
  /** 표시·숫자 — 타자기 등폭 */
  mono: Platform.select({
    ios: 'Courier New',
    android: 'monospace',
    default: '"Courier New", Courier, ui-monospace, monospace',
  }) as string,
} as const;

/** 이전 이름 호환 */
export const mono = fonts.mono;

export const colors = {
  bg: '#FBFAF7',
  surface: '#FFFFFF',
  surfaceAlt: '#F2F0EA',
  ink: '#14110E',
  text: '#14110E',
  textMuted: '#5C574F',
  textFaint: '#8E887E',

  line: '#DAD5CB',
  lineStrong: '#14110E',

  accent: '#1B3A5C',
  accentSoft: '#E8EDF3',

  warn: '#8A5A12',
  warnSoft: '#F5EBDA',
  danger: '#8C3323',
  dangerSoft: '#F5E3DF',

  trackEmpty: '#E6E2D9',
} as const;

export const spacing = {
  xs: 4,
  sm: 8,
  md: 12,
  lg: 16,
  xl: 24,
  xxl: 36,
} as const;

/**
 * 모서리. 완전한 직각은 인쇄물처럼 딱딱해 보이므로 아주 옅게만 굴린다.
 * 원형은 토글 손잡이처럼 움직이는 요소에만 쓴다.
 */
export const radius = {
  none: 0,
  sm: 4,
  md: 8,
  lg: 12,
  pill: 999,
} as const;

/** 카드에 얹는 아주 옅은 그림자. 면을 띄우기보다 경계를 부드럽게 만드는 용도다. */
export const elevation = {
  card: {
    shadowColor: '#14110E',
    shadowOpacity: 0.05,
    shadowRadius: 10,
    shadowOffset: { width: 0, height: 2 },
    elevation: 1,
  },
} as const;

export const type = {
  display: { fontFamily: fonts.serif, fontSize: 27, fontWeight: '700' as const, letterSpacing: -0.2 },
  title: { fontFamily: fonts.serif, fontSize: 20, fontWeight: '700' as const },
  subtitle: { fontFamily: fonts.serif, fontSize: 16, fontWeight: '700' as const },
  body: { fontFamily: fonts.serif, fontSize: 15, fontWeight: '400' as const },
  /** 라벨·버튼 — 타자기 */
  label: { fontFamily: fonts.mono, fontSize: 13, fontWeight: '700' as const, letterSpacing: 0.2 },
  /** 섹션 머리글 — 타자기 대문자, 넓은 자간 */
  eyebrow: { fontFamily: fonts.mono, fontSize: 10.5, fontWeight: '700' as const, letterSpacing: 2 },
  caption: { fontFamily: fonts.mono, fontSize: 11.5, fontWeight: '400' as const },
  numeral: { fontFamily: fonts.mono, fontSize: 14, fontWeight: '700' as const },
} as const;

export const hairline = 1;

/** 장식 기호 — 필사본의 단락 표시 */
export const ornament = {
  section: '❧',
  divider: '⁘',
  bullet: '·',
} as const;

export const layout = {
  content: { maxWidth: 560, width: '100%' as const, alignSelf: 'center' as const },
} as const;

/** 지연 단계 (§F4) */
export const lagStyle: Record<string, { label: string; fg: string; bg: string }> = {
  L0_NORMAL: { label: '정상', fg: colors.textMuted, bg: colors.surfaceAlt },
  L1_CAUTION: { label: '주의', fg: colors.warn, bg: colors.warnSoft },
  L2_DELAYED: { label: '지연', fg: colors.warn, bg: colors.warnSoft },
  L3_SERIOUS: { label: '심각', fg: colors.danger, bg: colors.dangerSoft },
  L4_NEGLECTED: { label: '방치', fg: colors.danger, bg: colors.dangerSoft },
};

export const paceStyle: Record<string, { label: string; fg: string; bg: string }> = {
  ON_TRACK: { label: '순항', fg: colors.accent, bg: colors.accentSoft },
  BEHIND: { label: '뒤처짐', fg: colors.warn, bg: colors.warnSoft },
  AT_RISK: { label: '위험', fg: colors.danger, bg: colors.dangerSoft },
};

export const statusLabel: Record<string, string> = {
  WANT_TO_READ: '읽고 싶은',
  READING: '읽는 중',
  PAUSED: '멈춤',
  FINISHED: '완독',
  ABANDONED: '하차',
};
