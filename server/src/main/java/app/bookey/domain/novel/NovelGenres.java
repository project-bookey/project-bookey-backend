package app.bookey.domain.novel;

import java.util.List;

/** 작품 장르이자 관심 장르로 고를 수 있는 목록. 앱의 장르 선택지가 이 목록을 그대로 쓴다. */
public final class NovelGenres {
    private NovelGenres() {}
    public static final List<String> ALL = List.of("판타지", "일상", "미스터리", "로맨스", "SF", "드라마", "무협", "공포");
    /** 관심 장르는 다섯 개까지 고른다. */
    public static final int MAX_INTERESTS = 5;
}
