import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { useLocalSearchParams } from 'expo-router';
import { useState } from 'react';
import {
  KeyboardAvoidingView, Platform, Pressable, ScrollView, StyleSheet, Text, TextInput, View,
} from 'react-native';

import { clubApi } from '@/api/endpoints';
import type { ClubPost } from '@/api/types';
import {
  Button, Card, Eyebrow, EmptyState, Loading, Numeral, Rule, Screen, Tag, formatRelative,
} from '@/components/ui';
import { colors, fonts, hairline, spacing, type, layout } from '@/theme';

const TYPE_LABEL: Record<string, string> = {
  DISCUSSION: '토론',
  QUESTION: '질문',
  QUOTE: '인용',
  NOTICE: '공지',
  CHECKPOINT: '체크포인트',
};

/**
 * 모임 토론 (§12.3).
 *
 * 스포일러 가드는 서버가 강제한다 — 내 진도보다 앞선 글은 본문 없이(masked=true) 내려온다.
 * 여기서는 그 사실을 사용자에게 설명하고, "그래도 볼래요"를 눌렀을 때만 서버에 해제를 요청한다.
 */
export default function ClubPostsScreen() {
  const { id } = useLocalSearchParams<{ id: string }>();
  const clubId = Number(id);
  const queryClient = useQueryClient();

  const [onlyMyRange, setOnlyMyRange] = useState(true);
  const [body, setBody] = useState('');
  const [anchorPage, setAnchorPage] = useState('');

  const club = useQuery({ queryKey: ['club', clubId], queryFn: () => clubApi.home(clubId) });
  const posts = useQuery({
    queryKey: ['club', clubId, 'posts', onlyMyRange],
    queryFn: () => clubApi.posts(clubId, onlyMyRange),
    enabled: Number.isFinite(clubId),
  });

  const myPage = club.data?.members.find((m) => m.isMe)?.currentPage ?? 0;

  const create = useMutation({
    mutationFn: () =>
      clubApi.createPost(clubId, {
        body: body.trim(),
        anchorPage: anchorPage ? Number(anchorPage) : undefined,
        spoilerLevel: anchorPage ? 'PAGE' : 'NONE',
      }),
    onSuccess: () => {
      setBody('');
      setAnchorPage('');
      queryClient.invalidateQueries({ queryKey: ['club', clubId, 'posts'] });
    },
  });

  const reveal = useMutation({
    mutationFn: (postId: number) => clubApi.reveal(clubId, postId),
    onSuccess: () => queryClient.invalidateQueries({ queryKey: ['club', clubId, 'posts'] }),
  });

  const react = useMutation({
    mutationFn: ({ postId, kind }: { postId: number; kind: string }) =>
      clubApi.react(clubId, postId, kind),
    onSuccess: () => queryClient.invalidateQueries({ queryKey: ['club', clubId, 'posts'] }),
  });

  const items = posts.data?.content ?? [];

  return (
    <Screen>
      <KeyboardAvoidingView
        style={{ flex: 1 }}
        behavior={Platform.OS === 'ios' ? 'padding' : undefined}
        keyboardVerticalOffset={90}
      >
        <View style={styles.filterBar}>
          <Pressable
            style={styles.filterToggle}
            onPress={() => setOnlyMyRange((prev) => !prev)}
          >
            <View style={[styles.checkbox, onlyMyRange && styles.checkboxOn]}>
              {onlyMyRange ? <Text style={styles.checkboxMark}>✓</Text> : null}
            </View>
            <Text style={styles.filterLabel}>내 진도까지만 보기</Text>
          </Pressable>
          <Numeral style={styles.myPage}>{myPage}쪽</Numeral>
        </View>

        {posts.isLoading ? <Loading /> : null}

        <ScrollView contentContainerStyle={styles.list}>
          {!posts.isLoading && items.length === 0 ? (
            <EmptyState
              title="아직 글이 없어요"
              description={
                onlyMyRange
                  ? '내 진도보다 앞선 글은 숨겨져 있습니다. 첫 글을 남겨보세요.'
                  : '첫 글을 남겨보세요. 페이지를 지정하면 진도가 느린 사람에게는 가려집니다.'
              }
            />
          ) : null}

          {items.map((post) => (
            <PostCard
              key={post.id}
              post={post}
              onReveal={() => reveal.mutate(post.id)}
              onReact={(kind) => react.mutate({ postId: post.id, kind })}
            />
          ))}
        </ScrollView>

        <View style={styles.composer}>
          <Rule />
          <View style={styles.composerRow}>
            <TextInput
              value={body}
              onChangeText={setBody}
              placeholder="이 책에 대해 이야기해요"
              placeholderTextColor={colors.textFaint}
              style={styles.composerInput}
              multiline
            />
          </View>
          <View style={styles.composerFooter}>
            <View style={styles.anchorField}>
              <Text style={styles.anchorLabel}>기준 쪽</Text>
              <TextInput
                value={anchorPage}
                onChangeText={(t) => setAnchorPage(t.replace(/[^0-9]/g, ''))}
                placeholder={String(myPage)}
                placeholderTextColor={colors.textFaint}
                keyboardType="number-pad"
                style={styles.anchorInput}
              />
              <Text style={styles.anchorHint}>비우면 전체 공개</Text>
            </View>
            <Button
              label="올리기"
              size="sm"
              disabled={body.trim().length === 0}
              loading={create.isPending}
              onPress={() => create.mutate()}
            />
          </View>
        </View>
      </KeyboardAvoidingView>
    </Screen>
  );
}

function PostCard({ post, onReveal, onReact }: {
  post: ClubPost;
  onReveal: () => void;
  onReact: (kind: string) => void;
}) {
  return (
    <Card style={styles.post}>
      <View style={styles.postHead}>
        <Text style={styles.author}>{post.authorNickname}</Text>
        <Tag label={TYPE_LABEL[post.type] ?? post.type} />
        {post.anchorPage != null ? (
          <Numeral style={styles.anchorBadge}>p.{post.anchorPage}</Numeral>
        ) : null}
        <Text style={styles.time}>{formatRelative(post.createdAt)}</Text>
      </View>

      {post.masked ? (
        <Pressable style={styles.masked} onPress={onReveal}>
          <View style={styles.maskedLines}>
            <View style={[styles.maskedLine, { width: '92%' }]} />
            <View style={[styles.maskedLine, { width: '78%' }]} />
            <View style={[styles.maskedLine, { width: '54%' }]} />
          </View>
          <Text style={styles.maskedNotice}>
            {post.anchorPage != null
              ? `${post.anchorPage}쪽 기준 글입니다`
              : '완독자에게만 보이는 글입니다'}
          </Text>
          <Text style={styles.maskedAction}>그래도 볼래요</Text>
        </Pressable>
      ) : (
        <Text style={styles.body}>{post.body}</Text>
      )}

      {!post.masked ? (
        <View style={styles.postFooter}>
          <View style={styles.reactions}>
            {['LIKE', 'FIRE', 'CRY', 'THINK'].map((kind) => (
              <Pressable
                key={kind}
                onPress={() => onReact(kind)}
                style={[
                  styles.reaction,
                  post.myReactions.includes(kind) && styles.reactionOn,
                ]}
              >
                <Text
                  style={[
                    styles.reactionText,
                    post.myReactions.includes(kind) && styles.reactionTextOn,
                  ]}
                >
                  {reactionLabel(kind)}
                </Text>
              </Pressable>
            ))}
          </View>
          {post.commentCount > 0 ? (
            <Text style={styles.commentCount}>댓글 {post.commentCount}</Text>
          ) : null}
        </View>
      ) : null}

      {post.comments.length > 0 ? (
        <View style={styles.comments}>
          {post.comments.map((comment) => (
            <View key={comment.id} style={styles.comment}>
              <Text style={styles.commentAuthor}>{comment.authorNickname}</Text>
              <Text style={styles.commentBody}>{comment.body ?? '(가려진 댓글)'}</Text>
            </View>
          ))}
        </View>
      ) : null}
    </Card>
  );
}

function reactionLabel(kind: string): string {
  switch (kind) {
    case 'LIKE': return '좋아요';
    case 'FIRE': return '뜨겁다';
    case 'CRY': return '울컥';
    default: return '생각중';
  }
}

const styles = StyleSheet.create({
  filterBar: {
    ...layout.content,
    flexDirection: 'row',
    alignItems: 'center',
    justifyContent: 'space-between',
    paddingHorizontal: spacing.lg,
    paddingVertical: spacing.md,
    borderBottomWidth: hairline,
    borderBottomColor: colors.line,
  },
  filterToggle: { flexDirection: 'row', alignItems: 'center', gap: spacing.sm },
  checkbox: {
    width: 17,
    height: 17,
    borderRadius: 4,
    borderWidth: hairline,
    borderColor: colors.textFaint,
    alignItems: 'center',
    justifyContent: 'center',
  },
  checkboxOn: { backgroundColor: colors.ink, borderColor: colors.ink },
  checkboxMark: { color: '#FFFFFF', fontSize: 11, fontWeight: '700' },
  filterLabel: { ...type.label, color: colors.text },
  myPage: { fontSize: 12, color: colors.textFaint },
  list: { ...layout.content, padding: spacing.lg, gap: spacing.md, paddingBottom: spacing.xl },
  post: { gap: spacing.sm },
  postHead: { flexDirection: 'row', alignItems: 'center', gap: spacing.sm, flexWrap: 'wrap' },
  author: { ...type.label, color: colors.ink },
  anchorBadge: { fontSize: 11, color: colors.accent, fontWeight: '700' },
  time: { ...type.caption, color: colors.textFaint, marginLeft: 'auto' },
  body: { ...type.body, color: colors.text, lineHeight: 22 },
  masked: {
    borderWidth: hairline,
    borderColor: colors.line,
    borderStyle: 'dashed',
    borderRadius: 8,
    padding: spacing.md,
    gap: spacing.sm,
    backgroundColor: colors.surfaceAlt,
  },
  maskedLines: { gap: 6 },
  maskedLine: { height: 9, borderRadius: 4, backgroundColor: colors.trackEmpty },
  maskedNotice: { ...type.caption, color: colors.textMuted },
  maskedAction: { ...type.label, color: colors.accent },
  postFooter: {
    flexDirection: 'row',
    alignItems: 'center',
    justifyContent: 'space-between',
    marginTop: spacing.xs,
  },
  reactions: { flexDirection: 'row', gap: spacing.xs },
  reaction: {
    borderWidth: hairline,
    borderColor: colors.line,
    borderRadius: 999,
    paddingHorizontal: spacing.md,
    paddingVertical: 5,
  },
  reactionOn: { backgroundColor: colors.ink, borderColor: colors.ink },
  reactionText: { fontSize: 11, color: colors.textMuted, fontWeight: '600' },
  reactionTextOn: { color: '#FFFFFF' },
  commentCount: { ...type.caption, color: colors.textFaint },
  comments: {
    borderTopWidth: hairline,
    borderTopColor: colors.line,
    paddingTop: spacing.sm,
    gap: spacing.sm,
  },
  comment: { gap: 2 },
  commentAuthor: { ...type.caption, color: colors.textMuted, fontWeight: '700' },
  commentBody: { ...type.caption, color: colors.text, lineHeight: 17 },
  composer: { ...layout.content, backgroundColor: colors.surface },
  composerRow: { paddingHorizontal: spacing.lg, paddingTop: spacing.md },
  composerInput: {
    minHeight: 44,
    maxHeight: 120,
    fontSize: 14,
    color: colors.text,
    textAlignVertical: 'top',
  },
  composerFooter: {
    flexDirection: 'row',
    alignItems: 'flex-end',
    justifyContent: 'space-between',
    paddingHorizontal: spacing.lg,
    paddingBottom: spacing.md,
    gap: spacing.md,
  },
  anchorField: { flexDirection: 'row', alignItems: 'center', gap: spacing.sm, flex: 1 },
  anchorLabel: { ...type.caption, color: colors.textMuted },
  anchorInput: {
    borderBottomWidth: hairline,
    borderBottomColor: colors.line,
    fontFamily: fonts.mono,
    fontSize: 14,
    color: colors.ink,
    minWidth: 44,
    paddingVertical: 2,
    textAlign: 'center',
  },
  anchorHint: { ...type.caption, color: colors.textFaint },
});
