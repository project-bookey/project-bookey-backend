CREATE TABLE novels (
    id BIGSERIAL PRIMARY KEY,
    owner_id BIGINT NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    is_public BOOLEAN NOT NULL DEFAULT TRUE,
    invite_code VARCHAR(32) NOT NULL UNIQUE,
    kind VARCHAR(10) NOT NULL CHECK (kind IN ('SOLO', 'RELAY')),
    status VARCHAR(12) NOT NULL CHECK (status IN ('RECRUITING', 'ONGOING', 'COMPLETED')),
    title VARCHAR(120) NOT NULL,
    description VARCHAR(500) NOT NULL DEFAULT '',
    genre VARCHAR(30) NOT NULL,
    cover_id BIGINT,
    member_limit INTEGER NOT NULL CHECK (member_limit BETWEEN 1 AND 10),
    chapter_limit INTEGER NOT NULL CHECK (chapter_limit BETWEEN 1 AND 100),
    turn_hours INTEGER NOT NULL CHECK (turn_hours BETWEEN 1 AND 168),
    chapter_count INTEGER NOT NULL DEFAULT 0,
    turn_number BIGINT NOT NULL DEFAULT 1,
    current_writer_id BIGINT REFERENCES users(id) ON DELETE SET NULL,
    turn_due_at TIMESTAMPTZ,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ DEFAULT now()
);
CREATE INDEX novels_feed ON novels(kind, status, created_at DESC, id DESC);
CREATE INDEX novels_due ON novels(turn_due_at) WHERE status = 'ONGOING';
CREATE TABLE novel_members (
    id BIGSERIAL PRIMARY KEY,
    novel_id BIGINT NOT NULL REFERENCES novels(id) ON DELETE CASCADE,
    user_id BIGINT NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    status VARCHAR(10) NOT NULL CHECK (status IN ('ACTIVE', 'PENDING', 'LEFT', 'REJECTED')),
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ DEFAULT now(),
    UNIQUE(novel_id, user_id)
);
CREATE TABLE novel_chapters (
    id BIGSERIAL PRIMARY KEY,
    novel_id BIGINT NOT NULL REFERENCES novels(id) ON DELETE CASCADE,
    author_id BIGINT REFERENCES users(id) ON DELETE SET NULL,
    chapter_number INTEGER NOT NULL,
    title VARCHAR(120) NOT NULL,
    body TEXT NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ DEFAULT now(),
    UNIQUE(novel_id, chapter_number)
);
CREATE TABLE novel_drafts (
    id BIGSERIAL PRIMARY KEY,
    novel_id BIGINT NOT NULL REFERENCES novels(id) ON DELETE CASCADE,
    user_id BIGINT NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    turn_number BIGINT NOT NULL,
    title VARCHAR(120) NOT NULL,
    body TEXT NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ DEFAULT now(),
    UNIQUE(novel_id, user_id)
);
CREATE TABLE novel_covers (
    id BIGSERIAL PRIMARY KEY,
    user_id BIGINT NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    novel_id BIGINT REFERENCES novels(id) ON DELETE SET NULL,
    storage_key VARCHAR(255) NOT NULL,
    url TEXT NOT NULL,
    width INTEGER,
    height INTEGER,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ DEFAULT now()
);
ALTER TABLE novels ADD CONSTRAINT novels_cover_fk FOREIGN KEY (cover_id) REFERENCES novel_covers(id) ON DELETE SET NULL;
