package app.bookey.common.storage;

import app.bookey.common.error.ApiException;
import app.bookey.common.error.ErrorCode;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

import java.io.InputStream;

/**
 * 사진 업로드를 꺼 두는 저장소({@code bookey.storage.type=none}).
 *
 * <p>운영 GCS 버킷이 아직 없어서 둔 임시 구현이다. 저장소를 GCS 로 강제하면 버킷이 준비될 때까지
 * 새 리비전이 아예 못 뜨고, 그렇다고 로컬 디스크로 떨어뜨리면 업로드는 200 으로 성공해 놓고
 * 인스턴스가 교체될 때 사진이 사라진다 — 조용한 데이터 유실이다. 그래서 세 번째 선택지로
 * <b>업로드만</b> 분명히 거절하고(503 {@code STORAGE_DISABLED}) 독후감·밑줄·댓글 등 나머지 기능은
 * 그대로 쓰게 한다.
 *
 * <p>켜는 법: 버킷을 만든 뒤(README "GCS 준비 (운영)") 배포 워크플로
 * {@code .github/workflows/deploy-cloud-run.yml} 의 {@code env_vars} 에
 * {@code STORAGE_TYPE=gcs} 와 {@code GCS_BUCKET=${{ vars.GCP_MEDIA_BUCKET }}} 두 줄을 넣으면
 * 이 빈 대신 {@link GcsStorageService} 가 뜬다.
 */
@Slf4j
@Service
@ConditionalOnProperty(prefix = "bookey.storage", name = "type", havingValue = "none")
public class NoopStorageService implements StorageService {

    public NoopStorageService() {
        log.warn("사진 업로드가 꺼져 있습니다(bookey.storage.type=none) — 업로드 API 는 503 으로 거절합니다.");
    }

    @Override
    public boolean enabled() {
        return false;
    }

    @Override
    public String store(String key, InputStream in, long size, String contentType) {
        throw ApiException.of(ErrorCode.STORAGE_DISABLED);
    }

    /**
     * 지울 파일이 애초에 없으므로 아무것도 하지 않는다.
     * 고아 사진 정리 배치가 이 메서드를 부르는데 여기서 예외를 던지면 잡이 통째로 죽는다 — 로그만 남긴다.
     */
    @Override
    public void delete(String key) {
        log.debug("저장소가 꺼져 있어 삭제를 건너뜁니다: key={}", key);
    }
}
