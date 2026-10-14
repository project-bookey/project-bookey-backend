package app.bookey.api.novel;

import app.bookey.api.novel.dto.NovelDtos.*;
import app.bookey.common.error.*;
import app.bookey.domain.novel.*;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.util.*;

/** 장르 목록, 소설 관심 장르, 베이스 작품 목록. */
@Service @RequiredArgsConstructor
public class NovelGenreService {
    private final NovelUserGenreRepository userGenres;
    private final NovelBaseWorkRepository baseWorks;

    public List<String> genres() { return NovelGenres.ALL; }

    @Transactional(readOnly = true)
    public List<String> mine(Long me) {
        return userGenres.findByUserIdOrderByIdAsc(me).stream().map(NovelUserGenre::getGenre).toList();
    }

    /** 고른 장르로 통째로 바꾼다. 빈 목록이면 관심 장르를 비운다. */
    @Transactional
    public List<String> replace(Long me, NovelGenresRequest request) {
        List<String> picked = request.genres().stream().map(String::strip).distinct().toList();
        if (picked.size() > NovelGenres.MAX_INTERESTS || !NovelGenres.ALL.containsAll(picked))
            throw ApiException.of(ErrorCode.INVALID_REQUEST);
        userGenres.deleteAllOfUser(me);
        userGenres.saveAll(picked.stream().map(genre -> new NovelUserGenre(me, genre)).toList());
        return picked;
    }

    @Transactional(readOnly = true)
    public List<NovelBaseWorkView> baseWorks() {
        return baseWorks.findByActiveTrueOrderBySortOrderAscIdAsc().stream()
                .map(w -> new NovelBaseWorkView(w.getId(), w.getTitle(), w.getGenre(), w.getSynopsis(), w.getSource()))
                .toList();
    }
}
