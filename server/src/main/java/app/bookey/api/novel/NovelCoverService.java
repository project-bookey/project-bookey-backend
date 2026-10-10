package app.bookey.api.novel;

import app.bookey.api.novel.dto.NovelDtos.NovelCoverView;
import app.bookey.common.config.BookeyProperties;
import app.bookey.common.error.*;
import app.bookey.common.storage.*;
import app.bookey.common.support.RateLimiter;
import app.bookey.domain.novel.*;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;
import java.io.*;
import java.time.*;
import java.util.UUID;

@Service @RequiredArgsConstructor
public class NovelCoverService {
    private final NovelCoverRepository covers;
    private final StorageService storage;
    private final BookeyProperties properties;
    private final RateLimiter limiter;
    public NovelCoverView upload(Long me, MultipartFile file) {
        if (!storage.enabled()) throw ApiException.of(ErrorCode.STORAGE_DISABLED);
        limiter.require("novel:cover:" + me, 20, Duration.ofMinutes(1));
        if (file == null || file.isEmpty()) throw ApiException.of(ErrorCode.INVALID_REQUEST);
        if (file.getSize() > properties.storage().image().maxBytes()) throw ApiException.of(ErrorCode.IMAGE_TOO_LARGE);
        ImageSniffer.ImageType type;
        try (InputStream in = file.getInputStream()) { type = ImageSniffer.sniff(in.readNBytes(64 * 1024)); }
        catch (IOException e) { throw ApiException.of(ErrorCode.STORAGE_ERROR); }
        if (type == null) throw ApiException.of(ErrorCode.UNSUPPORTED_IMAGE_TYPE);
        String key = "novels/" + me + "/" + UUID.randomUUID() + "." + type.extension();
        String url;
        try (InputStream in = file.getInputStream()) { url = storage.store(key, in, file.getSize(), type.contentType()); }
        catch (IOException e) { throw ApiException.of(ErrorCode.STORAGE_ERROR); }
        try {
            NovelCover c = covers.save(new NovelCover(me, key, url, type.width(), type.height()));
            return new NovelCoverView(c.getId(), c.getUrl(), c.getWidth(), c.getHeight());
        } catch (RuntimeException e) {
            try { storage.delete(key); } catch (RuntimeException deletion) { e.addSuppressed(deletion); }
            throw e;
        }
    }
    /** 첨부와 같은 행 잠금으로 경합한다 — 방금 표지로 붙인 파일은 지우지 않는다. */
    @Transactional
    public void deleteOrphan(Long id, Instant cutoff) {
        covers.lockById(id).filter(c -> c.getNovelId() == null && c.getUpdatedAt().isBefore(cutoff)).ifPresent(c -> {
            storage.delete(c.getStorageKey()); covers.delete(c);
        });
    }
}
