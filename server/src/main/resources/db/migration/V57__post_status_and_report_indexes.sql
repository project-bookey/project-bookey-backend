-- 독후감 숨김·삭제 — 리뷰·모임 글과 같은 VISIBLE | HIDDEN | DELETED 상태를 둔다.
-- 신고·관리자 조치로 숨기면 작성자만 보고, 지우면 아무도 못 본다(기록은 남는다).
ALTER TABLE posts ADD COLUMN status VARCHAR(10) NOT NULL DEFAULT 'VISIBLE';

-- 관리자 콘텐츠 검수에서 숨김·삭제된 글을 모아 볼 때
CREATE INDEX idx_posts_moderated ON posts (status, updated_at DESC) WHERE status <> 'VISIBLE';

-- 신고 대상별 신고 목록과 신고자별 신고 이력
CREATE INDEX IF NOT EXISTS idx_abuse_reports_target ON abuse_reports (target_type, target_id, status);
CREATE INDEX IF NOT EXISTS idx_abuse_reports_reporter ON abuse_reports (reporter_id, created_at DESC);
