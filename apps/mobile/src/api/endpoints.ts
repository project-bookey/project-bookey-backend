import { api } from './client';
import type {
  BookSummary, Checkpoint, ClubHome, ClubPost, ClubPreview, ClubResult, ClubSummary,
  LibrarySummary, Me, Notification, NudgeMessageKey, Page, ReadingRecord, ReadingStatus,
  Review, Session, SessionEndResult, StatsSummary, TokenResponse, VerificationPreview,
} from './types';

export const authApi = {
  devLogin: (token: string, nickname?: string) =>
    api<TokenResponse>('/api/v1/auth/social', {
      method: 'POST',
      auth: false,
      body: { provider: 'DEV', token, nickname },
    }),
  logout: () => api<void>('/api/v1/auth/logout', { method: 'POST' }),
  me: () => api<Me>('/api/v1/me'),
  updateProfile: (body: { nickname?: string; avatarUrl?: string }) =>
    api<Me>('/api/v1/me', { method: 'PATCH', body }),
};

export const bookApi = {
  search: (keyword: string) => api<BookSummary[]>('/api/v1/books', { query: { keyword } }),
  byIsbn: (isbn13: string) => api<BookSummary>(`/api/v1/books/isbn/${isbn13}`),
  detail: (bookId: number) => api<{ book: BookSummary; description?: string }>(`/api/v1/books/${bookId}`),
  createManual: (body: { title: string; author?: string; totalPages: number }) =>
    api<BookSummary>('/api/v1/books', { method: 'POST', body }),
  reviews: (bookId: number, verifiedOnly = false) =>
    api<Page<Review>>(`/api/v1/books/${bookId}/reviews`, { query: { verifiedOnly } }),
};

export const libraryApi = {
  list: (status?: ReadingStatus) =>
    api<Page<ReadingRecord>>('/api/v1/library', { query: { status, size: 50 } }),
  summary: () => api<LibrarySummary>('/api/v1/library/summary'),
  detail: (recordId: number) => api<ReadingRecord>(`/api/v1/library/${recordId}`),
  add: (body: { bookId: number; status?: ReadingStatus; targetFinishDate?: string; totalPagesOverride?: number }) =>
    api<ReadingRecord>('/api/v1/library', { method: 'POST', body }),
  updateGoal: (recordId: number, body: { targetFinishDate?: string; totalPagesOverride?: number }) =>
    api<ReadingRecord>(`/api/v1/library/${recordId}/goal`, { method: 'PATCH', body }),
  updateProgress: (recordId: number, currentPage: number) =>
    api<ReadingRecord>(`/api/v1/library/${recordId}/progress`, { method: 'PATCH', body: { currentPage } }),
  pause: (recordId: number) => api<ReadingRecord>(`/api/v1/library/${recordId}/pause`, { method: 'POST' }),
  resume: (recordId: number) => api<ReadingRecord>(`/api/v1/library/${recordId}/resume`, { method: 'POST' }),
  finish: (recordId: number, rating?: number) =>
    api<ReadingRecord>(`/api/v1/library/${recordId}/finish`, { method: 'POST', body: { rating } }),
  abandon: (recordId: number, reason: string) =>
    api<ReadingRecord>(`/api/v1/library/${recordId}/abandon`, { method: 'POST', body: { reason } }),
};

export const sessionApi = {
  current: () => api<Session | null>('/api/v1/sessions/current'),
  start: (readingRecordId: number, startPage?: number) =>
    api<Session>('/api/v1/sessions/start', { method: 'POST', body: { readingRecordId, startPage } }),
  end: (sessionId: number, body: { endPage?: number; foregroundRatio?: number; interactionCount?: number; memo?: string }) =>
    api<SessionEndResult>(`/api/v1/sessions/${sessionId}/end`, { method: 'POST', body }),
  manual: (body: { readingRecordId: number; startedAt: string; durationSec: number; startPage?: number; endPage?: number; memo?: string }) =>
    api<SessionEndResult>('/api/v1/sessions/manual', { method: 'POST', body }),
  listByRecord: (readingRecordId: number) =>
    api<Session[]>('/api/v1/sessions', { query: { readingRecordId } }),
};

export const statsApi = {
  summary: (days = 90) => api<StatsSummary>('/api/v1/stats', { query: { days } }),
};

export const clubApi = {
  myClubs: () => api<Page<ClubSummary>>('/api/v1/clubs', { query: { size: 50 } }),
  publicClubs: () => api<Page<ClubPreview>>('/api/v1/clubs/public'),
  preview: (code: string) => api<ClubPreview>('/api/v1/clubs/preview', { query: { code } }),
  join: (code: string, body: { adoptTargetDate: boolean; shareProgress: boolean }) =>
    api<ClubHome>('/api/v1/clubs/join', { method: 'POST', body: { code, ...body } }),
  create: (body: {
    name: string; description?: string; bookId: number; startsAt: string; endsAt: string;
    visibility?: string; memberLimit?: number; autoCheckpoints?: boolean; allowNudge?: boolean;
  }) => api<ClubHome>('/api/v1/clubs', { method: 'POST', body }),
  home: (clubId: number) => api<ClubHome>(`/api/v1/clubs/${clubId}`),
  result: (clubId: number) => api<ClubResult>(`/api/v1/clubs/${clubId}/result`),
  rotateCode: (clubId: number) =>
    api<{ joinCode: string }>(`/api/v1/clubs/${clubId}/rotate-code`, { method: 'POST' }),
  updateSharing: (clubId: number, body: { shareProgress?: boolean; allowNudge?: boolean }) =>
    api<void>(`/api/v1/clubs/${clubId}/sharing`, { method: 'PATCH', body }),
  leave: (clubId: number) => api<void>(`/api/v1/clubs/${clubId}/me`, { method: 'DELETE' }),
  end: (clubId: number) => api<void>(`/api/v1/clubs/${clubId}/end`, { method: 'POST' }),
  nudge: (clubId: number, toUserId: number, messageKey: NudgeMessageKey) =>
    api<{ remainingToday: number }>(`/api/v1/clubs/${clubId}/nudges`, {
      method: 'POST',
      body: { toUserId, messageKey },
    }),
  posts: (clubId: number, onlyMyRange: boolean) =>
    api<Page<ClubPost>>(`/api/v1/clubs/${clubId}/posts`, { query: { onlyMyRange, size: 50 } }),
  createPost: (clubId: number, body: {
    type?: string; body: string; anchorPage?: number; spoilerLevel?: string; parentId?: number;
  }) => api<ClubPost>(`/api/v1/clubs/${clubId}/posts`, { method: 'POST', body }),
  reveal: (clubId: number, postId: number) =>
    api<ClubPost>(`/api/v1/clubs/${clubId}/posts/${postId}/reveal`, { method: 'POST' }),
  react: (clubId: number, postId: number, kind: string) =>
    api<void>(`/api/v1/clubs/${clubId}/posts/${postId}/reactions`, { method: 'POST', body: { kind } }),
};

export const notificationApi = {
  list: () => api<Page<Notification>>('/api/v1/notifications', { query: { size: 50 } }),
  open: (id: number) => api<void>(`/api/v1/notifications/${id}/open`, { method: 'POST' }),
  updateSettings: (body: Record<string, unknown>) =>
    api<void>('/api/v1/notifications/settings', { method: 'PATCH', body }),
};

export const reviewApi = {
  preview: (readingRecordId: number) =>
    api<VerificationPreview>('/api/v1/reviews/preview', { query: { readingRecordId } }),
  create: (body: { readingRecordId: number; rating?: number; body: string; tags?: string[] }) =>
    api<Review>('/api/v1/reviews', { method: 'POST', body }),
  mine: () => api<Page<Review>>('/api/v1/reviews/me'),
};

export type { Checkpoint };
