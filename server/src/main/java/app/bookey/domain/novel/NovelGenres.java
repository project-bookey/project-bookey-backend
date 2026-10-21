package app.bookey.domain.novel;

import java.util.List;

/** 작품 장르이자 관심 장르로 고를 수 있는 목록. 앱의 장르 선택지가 이 목록을 그대로 쓴다. */
public final class NovelGenres {
    private NovelGenres() {}
    public static final List<String> ALL = List.of("판타지", "현대판타지", "로맨스", "로맨스판타지", "무협", "미스터리", "스릴러",
            "공포", "SF", "일상", "드라마", "역사", "코미디");
    /** 관심 장르는 다섯 개까지 고른다. */
    public static final int MAX_INTERESTS = 5;
}
