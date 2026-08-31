import { adminApi } from './api';
import type {
  AdminProfile, AuditRow, BookRow, ClubRow, ClubStatus, Dashboard, LoginResponse,
  ModerationResolution, ModerationRow, ModerationSource, ModerationStatus, NotificationStats,
  OpsFlagRow, Page, ReviewRow, SanctionType, UserDetail, UserRow, UserStatus, VerificationLevel,
} from './types';

export const authApi = {
  login: (email: string, password: string, totpCode?: string) =>
    adminApi<LoginResponse>('/admin/v1/auth/login', {
      method: 'POST',
      auth: false,
      body: { email, password, totpCode: totpCode || undefined },
    }),
  me: () => adminApi<AdminProfile>('/admin/v1/auth/me'),
};

export const dashboardApi = {
  get: () => adminApi<Dashboard>('/admin/v1/dashboard'),
};

export const usersApi = {
  list: (keyword?: string, status?: UserStatus, page = 0) =>
    adminApi<Page<UserRow>>('/admin/v1/users', { query: { keyword, status, page, size: 20 } }),
  detail: (userId: number, revealReason?: string) =>
    adminApi<UserDetail>(`/admin/v1/users/${userId}`, { query: { revealReason } }),
  sanction: (userId: number, body: { type: SanctionType; reason: string; durationDays?: number }) =>
    adminApi<void>(`/admin/v1/users/${userId}/sanctions`, { method: 'POST', body }),
  releaseSanction: (userId: number, sanctionId: number, reason: string) =>
    adminApi<void>(`/admin/v1/users/${userId}/sanctions/${sanctionId}`, {
      method: 'DELETE',
      query: { reason },
    }),
};

export const booksApi = {
  list: (keyword?: string, page = 0) =>
    adminApi<Page<BookRow>>('/admin/v1/books', { query: { keyword, page, size: 20 } }),
  update: (bookId: number, body: Record<string, unknown>) =>
    adminApi<void>(`/admin/v1/books/${bookId}`, { method: 'PATCH', body }),
};

export const moderationApi = {
  queue: (status?: ModerationStatus, sourceType?: ModerationSource, page = 0) =>
    adminApi<Page<ModerationRow>>('/admin/v1/moderation', {
      query: { status, sourceType, page, size: 20 },
    }),
  assign: (ticketId: number) =>
    adminApi<void>(`/admin/v1/moderation/${ticketId}/assign`, { method: 'POST' }),
  resolve: (
    ticketId: number,
    body: {
      resolution: ModerationResolution;
      note?: string;
      sanction?: { type: SanctionType; reason: string; durationDays?: number };
    },
  ) => adminApi<void>(`/admin/v1/moderation/${ticketId}/resolve`, { method: 'POST', body }),
};

export const reviewsApi = {
  list: (bookId?: number, page = 0) =>
    adminApi<Page<ReviewRow>>('/admin/v1/reviews', { query: { bookId, page, size: 20 } }),
  overrideVerification: (reviewId: number, level: VerificationLevel, reason: string) =>
    adminApi<void>(`/admin/v1/reviews/${reviewId}/verification`, {
      method: 'POST',
      body: { level, reason },
    }),
};

export const clubsApi = {
  list: (keyword?: string, status?: ClubStatus, page = 0) =>
    adminApi<Page<ClubRow>>('/admin/v1/clubs', { query: { keyword, status, page, size: 20 } }),
  forceEnd: (clubId: number, reason: string) =>
    adminApi<void>(`/admin/v1/clubs/${clubId}/force-end`, { method: 'POST', body: { reason } }),
  rotateCode: (clubId: number, reason: string) =>
    adminApi<{ joinCode: string }>(`/admin/v1/clubs/${clubId}/rotate-code`, {
      method: 'POST',
      body: { reason },
    }),
};

export const opsApi = {
  notificationStats: () => adminApi<NotificationStats>('/admin/v1/notifications/stats'),
  flags: () => adminApi<OpsFlagRow[]>('/admin/v1/ops-flags'),
  updateFlag: (key: string, enabled: boolean, note?: string) =>
    adminApi<void>(`/admin/v1/ops-flags/${key}`, { method: 'PATCH', body: { enabled, note } }),
};

export const auditApi = {
  list: (adminId?: number, action?: string, page = 0) =>
    adminApi<Page<AuditRow>>('/admin/v1/audit-logs', { query: { adminId, action, page, size: 50 } }),
};
