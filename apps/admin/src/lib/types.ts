export type Page<T> = {
  content: T[];
  page: number;
  size: number;
  totalElements: number;
  totalPages: number;
  hasNext: boolean;
};

export type AdminRole = 'SUPER_ADMIN' | 'OPERATOR' | 'SUPPORT' | 'VIEWER';

export type AdminProfile = {
  id: number;
  email: string;
  name: string;
  role: AdminRole;
  totpEnabled: boolean;
  lastLoginAt?: string;
};

export type LoginResponse = {
  accessToken?: string;
  expiresInSec: number;
  totpRequired: boolean;
  admin?: AdminProfile;
};

export type Dashboard = {
  totalUsers: number;
  activeUsersToday: number;
  readingSessionsToday: number;
  finishedBooksToday: number;
  verifiedReviewRatio: number;
  pendingModeration: number;
  overdueModeration: number;
  activeClubs: number;
  notificationConversionRate7d: number;
};

export type UserStatus = 'ACTIVE' | 'WRITE_BANNED' | 'SUSPENDED' | 'TERMINATED';

export type UserRow = {
  id: number;
  handle: string;
  nickname: string;
  maskedEmail?: string;
  status: UserStatus;
  createdAt: string;
  booksReading: number;
  booksFinished: number;
};

export type SanctionType = 'WARN' | 'WRITE_BAN' | 'SUSPEND' | 'TERMINATE';

export type SanctionRow = {
  id: number;
  type: SanctionType;
  reason: string;
  startsAt: string;
  endsAt?: string;
  releasedAt?: string;
  adminId: number;
};

export type UserDetail = {
  id: number;
  handle: string;
  nickname: string;
  email?: string;
  status: UserStatus;
  createdAt: string;
  totalSessions: number;
  totalDurationSec: number;
  reviewCount: number;
  clubCount: number;
  sanctions: SanctionRow[];
};

export type BookRow = {
  id: number;
  isbn13?: string;
  title: string;
  author?: string;
  publisher?: string;
  totalPages?: number;
  source: string;
  userCreated: boolean;
  createdAt: string;
};

export type ModerationSource = 'REVIEW' | 'POST' | 'CLUB_POST' | 'CLUB' | 'USER';
export type ModerationStatus = 'PENDING' | 'IN_REVIEW' | 'RESOLVED';
export type ModerationResolution = 'KEEP' | 'HIDE' | 'DELETE' | 'SANCTION';

export type ModerationRow = {
  id: number;
  sourceType: ModerationSource;
  sourceId: number;
  reason: string;
  reportCount: number;
  priority: number;
  slaDueAt: string;
  overdue: boolean;
  status: ModerationStatus;
  assignedAdminId?: number;
  contentPreview?: string;
  authorId?: number;
  authorNickname?: string;
};

export type VerificationLevel = 'VERIFIED_FULL' | 'VERIFIED_PARTIAL' | 'UNVERIFIED' | 'FLAGGED';

export type ReviewRow = {
  id: number;
  bookId: number;
  bookTitle?: string;
  authorId: number;
  authorNickname?: string;
  rating?: number;
  body: string;
  verificationLevel: VerificationLevel;
  verificationSnapshot?: Record<string, unknown>;
  reportCount: number;
  status: string;
  createdAt: string;
};

export type ClubStatus = 'RECRUITING' | 'ACTIVE' | 'ENDED' | 'ARCHIVED';

export type ClubRow = {
  id: number;
  name: string;
  joinCode: string;
  status: ClubStatus;
  memberCount: number;
  memberLimit: number;
  startsAt: string;
  endsAt: string;
  ownerId: number;
  ownerNickname?: string;
  postCount: number;
  createdAt: string;
};

export type NotificationStats = {
  sent7d: number;
  converted7d: number;
  conversionRate: number;
  pushEnabled: boolean;
};

export type OpsFlagRow = {
  key: string;
  enabled: boolean;
  note?: string;
  updatedAt: string;
};

export type AuditRow = {
  id: number;
  adminId: number;
  action: string;
  targetType?: string;
  targetId?: number;
  reason?: string;
  ip?: string;
  createdAt: string;
};
