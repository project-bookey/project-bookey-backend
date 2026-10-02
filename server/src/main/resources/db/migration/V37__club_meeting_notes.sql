-- 모임 공유 노트 — 모임(약속) 하나에 대형노트 한 권. 클럽 활성 멤버 누구나 함께 고친다.
-- document 는 앱이 소유한 JSON({v, paper, kind, elements}) 이라 서버는 요소 id 기준 연산(upsert/delete)만 적용하고
-- 요소 내용은 해석하지 않는다. 노트는 첫 편집 때 만들어진다(조회만으로는 행이 생기지 않는다).
CREATE TABLE club_meeting_notes (
    id            BIGSERIAL PRIMARY KEY,
    club_id       BIGINT      NOT NULL REFERENCES clubs(id) ON DELETE CASCADE,
    meeting_id    BIGINT      NOT NULL UNIQUE REFERENCES club_meetings(id) ON DELETE CASCADE,
    document      JSONB       NOT NULL DEFAULT '{}'::jsonb,
    version       INT         NOT NULL DEFAULT 0,       -- 연산이 적용될 때마다 +1. 앱이 놓친 연산이 있는지 판단하는 기준
    element_count INT         NOT NULL DEFAULT 0,       -- 피드에서 빈 노트를 거르려고 따로 둔다
    updated_by    BIGINT      REFERENCES users(id) ON DELETE SET NULL,
    created_at    TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at    TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX idx_club_meeting_notes_feed ON club_meeting_notes(club_id, updated_at DESC) WHERE element_count > 0;

-- 노트에 한 번이라도 손댄 멤버 — 피드 셀의 아바타.
CREATE TABLE club_meeting_note_contributors (
    note_id    BIGINT      NOT NULL REFERENCES club_meeting_notes(id) ON DELETE CASCADE,
    user_id    BIGINT      NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    PRIMARY KEY (note_id, user_id)
);

-- 노트 사진. 업로드 시에는 note_id 가 NULL(임시)이고, 그 사진을 참조하는 요소가 노트에 들어오면 채워진다.
-- 문서에서 빠지면 다시 비우고, 24시간 안에 어느 노트에도 붙지 않은 사진은 배치가 파일과 행을 지운다.
CREATE TABLE club_meeting_note_images (
    id           BIGSERIAL PRIMARY KEY,
    club_id      BIGINT       NOT NULL REFERENCES clubs(id) ON DELETE CASCADE,
    meeting_id   BIGINT       NOT NULL REFERENCES club_meetings(id) ON DELETE CASCADE,
    user_id      BIGINT       NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    note_id      BIGINT       REFERENCES club_meeting_notes(id) ON DELETE SET NULL,
    storage_key  VARCHAR(255) NOT NULL UNIQUE,
    url          TEXT         NOT NULL,
    content_type VARCHAR(40)  NOT NULL,
    byte_size    INT          NOT NULL,
    width        INT,
    height       INT,
    created_at   TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at   TIMESTAMPTZ  NOT NULL DEFAULT now()
);
CREATE INDEX idx_club_meeting_note_images_note ON club_meeting_note_images(note_id);
CREATE INDEX idx_club_meeting_note_images_orphan ON club_meeting_note_images(updated_at) WHERE note_id IS NULL;
