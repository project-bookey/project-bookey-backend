package app.bookey.batch;

import app.bookey.common.storage.StorageService;
import app.bookey.domain.club.ClubNoteImage;
import app.bookey.domain.club.ClubNoteImageRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.Instant;
import java.util.List;

/**
 * 모임 노트북 고아 사진 정리 — {@link PostImageCleanupJob} 과 같은 정책을 노트북 사진에 적용한다.
 *
 * <p>업로드한 사진은 page_id 없이 임시로 남는다 — 24시간 안에 어느 페이지 문서에도 참조되지 않으면 파일과 행을 지운다.
 * 문서에서 뺀 사진과 지운 페이지의 사진도 page_id 만 비우고 남겨 두므로 같은 경로로 회수된다(떼어진 시점부터 24시간).
 *
 * <p>행을 조건부로 먼저 지우고, 정말 지워진 건에 대해서만 파일을 지운다 — 조회 뒤 문서가 그 사진을 다시 참조했으면
 * {@link ClubNoteImageRepository#deleteIfDetached} 가 0 행이라 행도 파일도 건드리지 않는다.
 * 잡 전체를 한 트랜잭션으로 묶지 않아 건별 실패가 나머지를 막지 않는다.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ClubNoteImageCleanupJob {

    /** 붙지 않은 채 이 시간이 지나면 고아로 본다. */
    static final Duration ORPHAN_TTL = Duration.ofHours(24);

    private final ClubNoteImageRepository imageRepository;
    private final StorageService storage;

    /** 독후감 사진 정리(04:20) 10분 뒤. */
    @Scheduled(cron = "0 30 4 * * *", zone = "Asia/Seoul")
    public void cleanup() {
        run(Instant.now());
    }

    /** 테스트·수동 호출용. 지운 행 수를 돌려준다. */
    public int run(Instant now) {
        List<ClubNoteImage> orphans = imageRepository.findAllByPageIdIsNullAndUpdatedAtBefore(now.minus(ORPHAN_TTL));
        int deleted = 0;
        for (ClubNoteImage image : orphans) {
            if (deleteRow(image)) {
                deleted++;
                deleteFile(image);
            }
        }
        log.info("ClubNoteImageCleanupJob: {} orphan images found, {} deleted", orphans.size(), deleted);
        return deleted;
    }

    /** 여전히 고아일 때만 행을 지운다. 0 이면 조회 뒤에 문서에 붙었거나 이미 지워진 것 — 정상적인 경합이라 로그도 남기지 않는다. */
    private boolean deleteRow(ClubNoteImage image) {
        try {
            return imageRepository.deleteIfDetached(image.getId()) == 1;
        } catch (Exception e) {
            log.error("노트북 고아 사진 행 삭제 실패: id={} key={}", image.getId(), image.getStorageKey(), e);
            return false;
        }
    }

    /** 행은 이미 지워졌으므로 파일 삭제가 실패해도 되돌리지 않는다 — 키를 warn 로그에 남겨 손으로 회수할 수 있게 한다. */
    private void deleteFile(ClubNoteImage image) {
        try {
            storage.delete(image.getStorageKey());
        } catch (Exception e) {
            log.warn("노트북 고아 사진 파일 삭제 실패(행은 이미 지움): id={} key={}", image.getId(), image.getStorageKey(), e);
        }
    }
}
