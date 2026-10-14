-- 릴레이노벨 개편 — 열매 배달(OPEN)과 모여 만들기(CIRCLE), 회차 가지, 베이스 작품, 소설 관심 장르.
-- 북키 테이블(users.preferred_categories 등)을 재사용하지 않고 소설 전용 테이블을 둔다.

CREATE TABLE novel_base_works (
    id         BIGSERIAL PRIMARY KEY,
    title      VARCHAR(120) NOT NULL,
    genre      VARCHAR(30)  NOT NULL,
    synopsis   VARCHAR(500) NOT NULL,
    source     VARCHAR(120) NOT NULL DEFAULT '',
    active     BOOLEAN      NOT NULL DEFAULT TRUE,
    sort_order INTEGER      NOT NULL DEFAULT 0,
    created_at TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ  DEFAULT now()
);
-- 저작권이 끝난 고전 샘플. 실제 베이스 작품은 나중에 채운다.
INSERT INTO novel_base_works (title, genre, synopsis, source, sort_order) VALUES
 ('별주부전', '판타지', '병든 용왕을 살리려면 토끼의 간이 필요하다. 충직한 자라는 육지로 올라가 꾀 많은 토끼를 용궁으로 데려오지만, 토끼는 간을 땅에 두고 왔다며 다시 뭍으로 돌아갈 궁리를 한다.', '조선 후기 판소리계 소설', 1),
 ('홍길동전', '판타지', '서자로 태어나 아버지를 아버지라 부르지 못하던 길동은 집을 떠나 도적 무리의 우두머리가 된다. 도술로 탐관오리의 재물을 빼앗아 백성에게 나누던 그는 마침내 바다 건너 새 나라를 세운다.', '허균, 조선 중기', 2),
 ('심청전', '드라마', '눈먼 아버지의 눈을 뜨게 하려고 공양미 삼백 석에 몸을 판 심청은 인당수에 뛰어든다. 용궁에서 되살아난 심청은 왕비가 되어 맹인 잔치를 열고, 그곳에서 아버지를 다시 만난다.', '조선 후기 판소리계 소설', 3);

-- 초대 코드로 참가하는 방식은 없앤다(사용자 결정). 비공개 작품은 만들 때 고른 작가만 참가한다.
ALTER TABLE novels DROP COLUMN invite_code;
ALTER TABLE novels ADD COLUMN mode VARCHAR(10) NOT NULL DEFAULT 'CIRCLE' CHECK (mode IN ('OPEN', 'CIRCLE'));
ALTER TABLE novels ADD COLUMN delivery_count INTEGER CHECK (delivery_count BETWEEN 1 AND 10);
ALTER TABLE novels ADD COLUMN base_work_id BIGINT REFERENCES novel_base_works(id) ON DELETE SET NULL;
-- 열매로 퍼뜨리는 작품은 작가 수 제한이 없다.
ALTER TABLE novels ALTER COLUMN member_limit DROP NOT NULL;
-- 목표 회차(가지 깊이)는 최대 50.
UPDATE novels SET chapter_limit = 50 WHERE chapter_limit > 50;
ALTER TABLE novels DROP CONSTRAINT IF EXISTS novels_chapter_limit_check;
ALTER TABLE novels ADD CONSTRAINT novels_chapter_limit_check CHECK (chapter_limit BETWEEN 1 AND 50);

-- chapter_number 는 이제 가지의 깊이(뿌리 = 1)라 한 작품에 같은 번호가 여럿 있을 수 있다.
ALTER TABLE novel_chapters ADD COLUMN parent_chapter_id BIGINT REFERENCES novel_chapters(id) ON DELETE SET NULL;
UPDATE novel_chapters child SET parent_chapter_id = parent.id
  FROM novel_chapters parent
 WHERE parent.novel_id = child.novel_id AND parent.chapter_number = child.chapter_number - 1;
ALTER TABLE novel_chapters DROP CONSTRAINT IF EXISTS novel_chapters_novel_id_chapter_number_key;
CREATE INDEX idx_novel_chapters_number ON novel_chapters(novel_id, chapter_number, id);
CREATE INDEX idx_novel_chapters_parent ON novel_chapters(parent_chapter_id);

-- 회차가 발행되면 무작위로 고른 사람에게 그 회차가 열매로 배달된다.
CREATE TABLE novel_deliveries (
    id                 BIGSERIAL PRIMARY KEY,
    novel_id           BIGINT      NOT NULL REFERENCES novels(id) ON DELETE CASCADE,
    chapter_id         BIGINT      NOT NULL REFERENCES novel_chapters(id) ON DELETE CASCADE,
    recipient_id       BIGINT      NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    status             VARCHAR(10) NOT NULL CHECK (status IN ('DELIVERED', 'WRITTEN', 'DECLINED', 'EXPIRED')),
    due_at             TIMESTAMPTZ NOT NULL,
    written_chapter_id BIGINT REFERENCES novel_chapters(id) ON DELETE SET NULL,
    created_at         TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at         TIMESTAMPTZ DEFAULT now(),
    UNIQUE (chapter_id, recipient_id)
);
-- 한 사람은 한 작품의 열매를 한 번에 하나만 든다(초안이 작품·사람마다 하나라서).
CREATE UNIQUE INDEX uq_novel_deliveries_holding ON novel_deliveries(novel_id, recipient_id) WHERE status = 'DELIVERED';
CREATE INDEX idx_novel_deliveries_recipient ON novel_deliveries(recipient_id, status, created_at DESC);
CREATE INDEX idx_novel_deliveries_due ON novel_deliveries(due_at) WHERE status = 'DELIVERED';

-- 소설 관심 장르 — 배달할 사람을 고를 때 작품 장르와 맞는 사람을 먼저 고른다.
CREATE TABLE novel_user_genres (
    id         BIGSERIAL PRIMARY KEY,
    user_id    BIGINT      NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    genre      VARCHAR(30) NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ DEFAULT now(),
    UNIQUE (user_id, genre)
);
CREATE INDEX idx_novel_user_genres_genre ON novel_user_genres(genre);
