package app.bookey.api.curation;

import app.bookey.api.book.dto.BookDtos.BookSummary;
import app.bookey.api.curation.dto.CurationDtos.EditorPickCreateRequest;
import app.bookey.api.curation.dto.CurationDtos.EditorPickUpdateRequest;
import app.bookey.api.curation.dto.CurationDtos.EditorPickView;
import app.bookey.common.error.ApiException;
import app.bookey.common.error.ErrorCode;
import app.bookey.domain.book.Book;
import app.bookey.domain.book.BookRepository;
import app.bookey.domain.curation.EditorPick;
import app.bookey.domain.curation.EditorPickRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class EditorPickAdminService {

    private final EditorPickRepository editorPickRepository;
    private final BookRepository bookRepository;

    @Transactional(readOnly = true)
    public List<EditorPickView> list() {
        List<EditorPick> picks = editorPickRepository.findAllByOrderBySortOrderAscIdAsc();
        Map<Long, Book> books = bookRepository.findAllById(
                        picks.stream().map(EditorPick::getBookId).toList())
                .stream().collect(Collectors.toMap(Book::getId, b -> b));
        return picks.stream()
                .filter(p -> books.containsKey(p.getBookId()))
                .map(p -> new EditorPickView(p.getId(), BookSummary.from(books.get(p.getBookId())),
                        p.getSortOrder(), p.getNote()))
                .toList();
    }

    @Transactional
    public EditorPickView create(EditorPickCreateRequest req) {
        Book book = bookRepository.findById(req.bookId())
                .orElseThrow(() -> ApiException.of(ErrorCode.BOOK_NOT_FOUND));
        if (editorPickRepository.existsByBookId(req.bookId())) {
            throw ApiException.of(ErrorCode.EDITOR_PICK_DUPLICATE);
        }
        EditorPick pick = EditorPick.builder()
                .bookId(req.bookId()).sortOrder(req.sortOrder()).note(req.note())
                .build();
        pick = editorPickRepository.save(pick);
        return new EditorPickView(pick.getId(), BookSummary.from(book), pick.getSortOrder(), pick.getNote());
    }

    @Transactional
    public EditorPickView update(Long id, EditorPickUpdateRequest req) {
        EditorPick pick = editorPickRepository.findById(id)
                .orElseThrow(() -> ApiException.of(ErrorCode.EDITOR_PICK_NOT_FOUND));
        pick.update(req.sortOrder(), req.note());
        Book book = bookRepository.findById(pick.getBookId())
                .orElseThrow(() -> ApiException.of(ErrorCode.BOOK_NOT_FOUND));
        return new EditorPickView(pick.getId(), BookSummary.from(book), pick.getSortOrder(), pick.getNote());
    }

    @Transactional
    public void delete(Long id) {
        EditorPick pick = editorPickRepository.findById(id)
                .orElseThrow(() -> ApiException.of(ErrorCode.EDITOR_PICK_NOT_FOUND));
        editorPickRepository.delete(pick);
    }
}
