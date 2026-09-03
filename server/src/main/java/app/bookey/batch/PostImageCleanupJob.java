package app.bookey.batch;

import app.bookey.common.storage.StorageService;
import app.bookey.domain.post.PostImage;
import app.bookey.domain.post.PostImageRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.Instant;
import java.util.List;

/**
 * 고아 이미지 정리 (§F7 독후감 사진).
 *
 * <p>정책: 업로드한 사진은 post_id 없이 임시로 남는다 — 24시간 안에 독후감에 붙지 않으면 파일과 행을 지운다.
 * 독후감 수정 때 목록에서 뺀 사진과 삭제된 독후감의 사진도 post_id 만 비우고 남겨 두므로 같은 경로로 회수된다
 * (그동안 URL 은 최대 ~24시간 더 살아 있다).
 *
 * <p>잡 전체를 한 트랜잭션으로 묶지 않는다 — 건별 삭제가 부분 실패해도 나머지는 진행되게 하기 위해서다.
 * 행 삭제는 리포지토리 메서드 자체의 트랜잭션으로 한 건씩 커밋된다.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class PostImageCleanupJob {

    /** 붙지 않은 채 이 시간이 지나면 고아로 본다. */
    static final Duration ORPHAN_TTL = Duration.ofHours(24);

    private final PostImageRepository imageRepository;
    private final StorageService storage;

    @Scheduled(cron = "0 20 4 * * *", zone = "Asia/Seoul")
    public void cleanup() {
        run(Instant.now());
    }

    /** 테스트·수동 호출용. 지운 행 수를 돌려준다. */
    public int run(Instant now) {
        List<PostImage> orphans = imageRepository.findAllByPostIdIsNullAndCreatedAtBefore(now.minus(ORPHAN_TTL));
        int deleted = 0;
        for (PostImage image : orphans) {
            deleteFile(image);
            try {
                imageRepository.delete(image);
                deleted++;
            } catch (Exception e) {
                log.error("고아 이미지 행 삭제 실패: id={} key={}", image.getId(), image.getStorageKey(), e);
            }
        }
        log.info("PostImageCleanupJob: {} orphan images found, {} deleted", orphans.size(), deleted);
        return deleted;
    }

    /**
     * 파일 삭제가 실패해도 행은 지운다 — 행이 없어지면 다음 실행에서 다시 찾을 수 없으므로
     * 키를 warn 로그에 남겨 손으로 회수할 수 있게 한다.
     */
    private void deleteFile(PostImage image) {
        try {
            storage.delete(image.getStorageKey());
        } catch (Exception e) {
            log.warn("고아 이미지 파일 삭제 실패(행은 지움): id={} key={}", image.getId(), image.getStorageKey(), e);
        }
    }
}
