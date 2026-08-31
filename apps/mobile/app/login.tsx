import { useRouter } from 'expo-router';
import { useState } from 'react';
import { KeyboardAvoidingView, Platform, ScrollView, StyleSheet, Text, View } from 'react-native';

import { API_BASE_URL } from '@/api/client';
import { Button, Field, Rule, Screen } from '@/components/ui';
import { useAuth } from '@/store/auth';
import { colors, fonts, spacing, type } from '@/theme';

/**
 * 로그인.
 * 운영에서는 Apple / Google / Kakao 버튼이 이 자리에 들어간다.
 * 로컬에서는 서버의 DEV provider 로 바로 들어간다.
 */
export default function LoginScreen() {
  const router = useRouter();
  const devLogin = useAuth((s) => s.devLogin);
  const [handle, setHandle] = useState('tester');
  const [nickname, setNickname] = useState('테스터');
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState<string | null>(null);

  const submit = async () => {
    setLoading(true);
    setError(null);
    try {
      await devLogin(handle.trim(), nickname.trim());
      router.replace('/(tabs)/home');
    } catch (e) {
      setError(e instanceof Error ? e.message : '로그인에 실패했습니다.');
    } finally {
      setLoading(false);
    }
  };

  return (
    <Screen>
      <KeyboardAvoidingView
        behavior={Platform.OS === 'ios' ? 'padding' : undefined}
        style={{ flex: 1 }}
      >
        <ScrollView contentContainerStyle={styles.container}>
          <View>
            <Text style={styles.logo}>bookey</Text>
            <View style={styles.logoRule} />
            <Text style={styles.tagline}>
              읽기로 한 책을 끝까지.{'\n'}읽은 사람만 리뷰를 쓴다.
            </Text>
          </View>

          <View>
            <Field
              label="개발용 계정 ID"
              value={handle}
              onChangeText={setHandle}
              autoCapitalize="none"
              autoCorrect={false}
              placeholder="tester"
              hint="같은 ID로 다시 로그인하면 같은 계정으로 들어갑니다."
            />
            <Field
              label="닉네임"
              value={nickname}
              onChangeText={setNickname}
              placeholder="테스터"
              error={error}
            />
            <Button label="시작하기" onPress={submit} loading={loading} />
          </View>

          <View style={styles.footer}>
            <Rule />
            <Text style={styles.meta}>API {API_BASE_URL}</Text>
          </View>
        </ScrollView>
      </KeyboardAvoidingView>
    </Screen>
  );
}

const styles = StyleSheet.create({
  container: {
    flexGrow: 1,
    justifyContent: 'center',
    padding: spacing.xl,
    gap: spacing.xxl,
    maxWidth: 460,
    width: '100%',
    alignSelf: 'center',
  },
  logo: {
    fontFamily: fonts.serif,
    fontSize: 40,
    fontWeight: '700',
    color: colors.ink,
    letterSpacing: 0.5,
  },
  logoRule: { width: 40, height: 3, backgroundColor: colors.ink, marginTop: spacing.md },
  tagline: { ...type.body, color: colors.textMuted, marginTop: spacing.lg, lineHeight: 23 },
  footer: { gap: spacing.md },
  meta: { ...type.caption, color: colors.textFaint, fontVariant: ['tabular-nums'] },
});
