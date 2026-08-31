import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { useRouter } from 'expo-router';
import { useState } from 'react';
import { FlatList, Pressable, StyleSheet, Text, TextInput, View } from 'react-native';

import { bookApi, libraryApi } from '@/api/endpoints';
import type { BookSummary } from '@/api/types';
import { BookCover } from '@/components/BookCover';
import { Button, EmptyState, Loading, Screen, Tag } from '@/components/ui';
import { colors, hairline, spacing, type, layout } from '@/theme';

/** 도서 검색 → 서재 등록 (§F1, §F2) */
export default function SearchScreen() {
  const router = useRouter();
  const queryClient = useQueryClient();
  const [input, setInput] = useState('');
  const [keyword, setKeyword] = useState('');

  const search = useQuery({
    queryKey: ['books', keyword],
    queryFn: () => bookApi.search(keyword),
    enabled: keyword.trim().length > 0,
  });

  const add = useMutation({
    mutationFn: (book: BookSummary) =>
      libraryApi.add({ bookId: book.id, status: 'READING' }),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['library'] });
      router.back();
    },
  });

  return (
    <Screen>
      <View style={styles.searchBar}>
        <TextInput
          value={input}
          onChangeText={setInput}
          onSubmitEditing={() => setKeyword(input)}
          placeholder="제목 · 저자 · ISBN"
          placeholderTextColor={colors.textFaint}
          returnKeyType="search"
          autoFocus
          style={styles.input}
        />
        <Button label="검색" size="sm" onPress={() => setKeyword(input)} />
      </View>

      {search.isLoading ? <Loading /> : null}

      <FlatList
        data={search.data ?? []}
        keyExtractor={(item) => String(item.id)}
        contentContainerStyle={styles.list}
        ItemSeparatorComponent={() => <View style={styles.separator} />}
        ListEmptyComponent={
          search.isLoading ? null : (
            <EmptyState
              title={keyword ? '결과가 없어요' : '읽을 책을 찾아보세요'}
              description={
                keyword
                  ? '외부 검색에 없는 책이라면 직접 등록할 수 있습니다. 제목·저자·총 페이지 수만 있으면 됩니다.'
                  : '제목이나 저자로 검색하면 총 페이지 수까지 함께 가져옵니다.'
              }
            />
          )
        }
        renderItem={({ item }) => (
          <Pressable style={styles.row} onPress={() => add.mutate(item)} disabled={add.isPending}>
            <BookCover url={item.coverUrl} title={item.title} width={44} />
            <View style={styles.rowBody}>
              <Text numberOfLines={2} style={styles.title}>{item.title}</Text>
              <Text numberOfLines={1} style={styles.author}>
                {item.author ?? '저자 미상'}
                {item.publisher ? ` · ${item.publisher}` : ''}
              </Text>
              <View style={styles.tagRow}>
                {item.totalPages ? (
                  <Tag label={`${item.totalPages}쪽`} />
                ) : (
                  <Tag label="페이지 수 없음" fg={colors.warn} bg={colors.warnSoft} />
                )}
                <Tag label={item.source} />
              </View>
            </View>
            <Text style={styles.addMark}>담기</Text>
          </Pressable>
        )}
      />
    </Screen>
  );
}

const styles = StyleSheet.create({
  searchBar: {
    ...layout.content,
    flexDirection: 'row',
    gap: spacing.sm,
    padding: spacing.lg,
    alignItems: 'center',
  },
  input: {
    flex: 1,
    borderWidth: hairline,
    borderColor: colors.line,
    borderRadius: 8,
    backgroundColor: colors.surface,
    paddingHorizontal: spacing.md,
    paddingVertical: spacing.md,
    fontSize: 15,
    color: colors.text,
  },
  list: { ...layout.content, paddingHorizontal: spacing.lg, paddingBottom: spacing.xxl },
  separator: { height: hairline, backgroundColor: colors.line },
  row: { flexDirection: 'row', gap: spacing.md, paddingVertical: spacing.md, alignItems: 'center' },
  rowBody: { flex: 1, gap: 3 },
  title: { ...type.subtitle, color: colors.ink },
  author: { ...type.caption, color: colors.textMuted },
  tagRow: { flexDirection: 'row', gap: spacing.xs, marginTop: 2 },
  addMark: { ...type.label, color: colors.accent },
});
