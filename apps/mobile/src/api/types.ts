/** 서버 응답 타입 (server/src/main/java/app/bookey/api/** 의 DTO 와 1:1). */

export type Page<T> = {
  content: T[];
  page: number;
  size: number;
  totalElements: number;
  totalPages: number;
  hasNext: boolean;
};

export type Me = {
  id: number;
  handle: string;
  nickname: string;
  email?: string;
  avatarUrl?: string;
  timezone: string;
  notifyTone: NotifyTone;
  quietHoursStart: number;
  quietHoursEnd: number;
  dailyNotifyCap: number;
  clubNotifyCap: number;
  allowNudge: boolean;
  status: string;
};

export type NotifyTone = 'GENTLE' | 'FACT' | 'SPARTA' | 'TSUNDERE' | 'SILENT';

export type TokenResponse = {
  accessToken: string;
  refreshToken: string;
  expiresInSec: number;
  newUser: boolean;
  user: Me;
};

export type BookSummary = {
  id: number;
  isbn13?: string;
  title: string;
  author?: string;
  publisher?: string;
  publishedAt?: string;
  totalPages?: number;
  coverUrl?: string;
  category?: string;
  source: string;
  needsPageInput: boolean;
};

export type ReadingStatus = 'WANT_TO_READ' | 'READING' | 'PAUSED' | 'FINISHED' | 'ABANDONED';

export type Progress = {
  currentPage: number;
  totalPages: number;
  completionRate?: number;
  remainingPages: number;
  requiredDailyPace?: number;
  actualDailyPace?: number;
  paceGap?: number;
  estimatedFinishDate?: string;
  daysSinceLastRead?: number;
  lagLevel: string;
  totalDurationSec: number;
};

export type ReadingRecord = {
  id: number;
  round: number;
  status: ReadingStatus;
  book?: BookSummary;
  progress: Progress;
  targetFinishDate?: string;
  startedAt?: string;
  finishedAt?: string;
  lastReadAt?: string;
  rating?: number;
  abandonReason?: string;
};

export type LibrarySummary = {
  reading: number;
  wantToRead: number;
  finished: number;
  abandoned: number;
  paused: number;
};

export type Session = {
  id: number;
  readingRecordId: number;
  startedAt: string;
  endedAt?: string;
  durationSec: number;
  startPage?: number;
  endPage?: number;
  readPages?: number;
  source: 'TIMER' | 'MANUAL';
  memo?: string;
  abuseFlags: string[];
  countedForVerification: boolean;
};

export type SessionEndResult = {
  session: Session;
  currentPage: number;
  completionRate?: number;
  lagLevel: string;
  bookFinished: boolean;
  clubs: { clubId: number; clubName?: string; rank: number; memberCount: number }[];
};

export type StatsSummary = {
  totalDurationSec: number;
  todayDurationSec: number;
  weekDurationSec: number;
  currentStreakDays: number;
  longestStreakDays: number;
  daily: { date: string; durationSec: number; pages: number; sessionCount: number }[];
};

// ── 모임 ─────────────────────────────────────────────────
export type ClubVisibility = 'CODE_ONLY' | 'LINK' | 'PUBLIC';
export type ClubStatus = 'RECRUITING' | 'ACTIVE' | 'ENDED' | 'ARCHIVED';
export type ClubRole = 'HOST' | 'MODERATOR' | 'MEMBER';

export type ClubSummary = {
  id: number;
  name: string;
  coverUrl?: string;
  book?: BookSummary;
  status: ClubStatus;
  memberCount: number;
  daysLeft: number;
  myCompletionRate?: number;
  averageCompletionRate?: number;
  unreadPostCount: number;
};

export type ClubPreview = {
  id: number;
  name: string;
  description?: string;
  book?: BookSummary;
  hostNickname?: string;
  memberCount: number;
  memberLimit: number;
  startsAt: string;
  endsAt: string;
  status: ClubStatus;
  alreadyMember: boolean;
  joinable: boolean;
  joinBlockedReason?: string;
};

export type MemberProgress = {
  userId: number;
  clubMemberId: number;
  nickname: string;
  avatarUrl?: string;
  role: ClubRole;
  isMe: boolean;
  shareProgress: boolean;
  currentPage?: number;
  completionRate?: number;
  totalDurationSec?: number;
  lastReadAt?: string;
  finished?: boolean;
  paceStatus?: string;
  nudgeable: boolean;
};

export type Checkpoint = {
  id: number;
  seq: number;
  title: string;
  targetPage: number;
  dueAt: string;
  evaluated: boolean;
  achievedCount: number;
  memberCount: number;
  myAchieved?: boolean;
};

export type ClubHome = {
  id: number;
  name: string;
  description?: string;
  coverUrl?: string;
  joinCode: string;
  visibility: ClubVisibility;
  status: ClubStatus;
  book?: BookSummary;
  startsAt: string;
  endsAt: string;
  daysLeft: number;
  memberCount: number;
  memberLimit: number;
  myRole: ClubRole;
  myShareProgress: boolean;
  myAllowNudge: boolean;
  myRank: number;
  averageCompletionRate?: number;
  members: MemberProgress[];
  checkpoints: Checkpoint[];
  nextCheckpoint?: Checkpoint;
};

export type ClubResult = {
  clubId: number;
  name: string;
  book?: BookSummary;
  memberCount: number;
  finishedCount: number;
  finishRate: number;
  totalDurationSec: number;
  members: MemberProgress[];
  bestQuotes: string[];
  topDiscussant?: string;
};

export type ClubPostType = 'DISCUSSION' | 'QUESTION' | 'QUOTE' | 'NOTICE' | 'CHECKPOINT';

export type ClubPost = {
  id: number;
  parentId?: number;
  type: ClubPostType;
  authorId: number;
  authorNickname: string;
  authorAvatarUrl?: string;
  /** 마스킹된 글은 body 가 없다 — 서버가 내려보내지 않는다. */
  body?: string;
  masked: boolean;
  anchorPage?: number;
  spoilerLevel: 'NONE' | 'PAGE' | 'BOOK';
  pinned: boolean;
  commentCount: number;
  reactionCount: number;
  myReactions: string[];
  createdAt: string;
  comments: ClubPost[];
};

export type NudgeMessageKey = 'READ_TOGETHER' | 'CHECKPOINT_SOON' | 'WAITING';

// ── 알림 · 리뷰 ──────────────────────────────────────────
export type Notification = {
  id: number;
  type: string;
  lagLevel?: number;
  readingRecordId?: number;
  clubId?: number;
  title: string;
  body: string;
  payload: Record<string, unknown>;
  scheduledAt: string;
  sentAt?: string;
  openedAt?: string;
};

export type VerificationLevel = 'VERIFIED_FULL' | 'VERIFIED_PARTIAL' | 'UNVERIFIED' | 'FLAGGED';

export type VerificationPreview = {
  expectedLevel: VerificationLevel;
  coverage: number;
  timerSessionCount: number;
  verifiedMinutes: number;
  requiredMinutes: number;
  flags: string[];
  canRate: boolean;
};

export type Review = {
  id: number;
  bookId: number;
  authorId: number;
  authorNickname: string;
  authorHandle?: string;
  rating?: number;
  body: string;
  tags: string[];
  hasSpoiler: boolean;
  verificationLevel: VerificationLevel;
  verificationSnapshot?: Record<string, unknown>;
  helpfulCount: number;
  createdAt: string;
};
