package app.bookey.batch;

import app.bookey.common.storage.StorageService;
import app.bookey.domain.club.ClubMeetingNoteImage;
import app.bookey.domain.club.ClubMeetingNoteImageRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.Instant;
import java.util.List;

/**
 * 모임 노트 고아 사진 정리 — {@link PostImageCleanupJob} 과 같은 정책.
 *
 * <p>업로드한 사진은 note_id 없이 임시로 남는다 — 24시간 안에 어느 노트에도 참조되지 않으면 파일과 행을 지운다.
 * 문서에서 뺀 사진도 note_id 만 비우고 남겨 두므로 같은 경로로 회수된다(떼어진 시점부터 24시간).
 * 행을 조건부로 먼저 지우고 정말 지워진 건만 파일을 지운다 — 조회 뒤 다시 참조된 사진은 건드리지 않는다.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ClubMeetingNoteImageCleanupJob {

    static final Duration ORPHAN_TTL = Duration.ofHours(24);

    private final ClubMeetingNoteImageRepository imageRepository;
    private final StorageService storage;

    /** 독후감 사진 정리(04:20) 10분 뒤. */
    @Scheduled(cron = "0 30 4 * * *", zone = "Asia/Seoul")
    public void cleanup() {
        run(Instant.now());
    }

    /** 테스트·수동 호출용. 지운 행 수를 돌려준다. */
    public int run(Instant now) {
        List<ClubMeetingNoteImage> orphans = imageRepository.findAllByNoteIdIsNullAndUpdatedAtBefore(now.minus(ORPHAN_TTL));
        int deleted = 0;
        for (ClubMeetingNoteImage image : orphans) {
            if (deleteRow(image)) {
                deleted++;
                deleteFile(image);
            }
        }
        log.info("ClubMeetingNoteImageCleanupJob: {} orphan images found, {} deleted", orphans.size(), deleted);
        return deleted;
    }

    private boolean deleteRow(ClubMeetingNoteImage image) {
        try {
            return imageRepository.deleteIfDetached(image.getId()) == 1;
        } catch (Exception e) {
            log.error("모임 노트 고아 사진 행 삭제 실패: id={} key={}", image.getId(), image.getStorageKey(), e);
            return false;
        }
    }

    /** 행은 이미 지워졌으므로 파일 삭제가 실패해도 되돌리지 않는다 — 키를 남겨 손으로 회수할 수 있게 한다. */
    private void deleteFile(ClubMeetingNoteImage image) {
        try {
            storage.delete(image.getStorageKey());
        } catch (Exception e) {
            log.warn("모임 노트 고아 사진 파일 삭제 실패(행은 이미 지움): id={} key={}", image.getId(), image.getStorageKey(), e);
        }
    }
}
