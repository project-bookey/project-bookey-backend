package app.bookey.common.storage;

import java.io.InputStream;

/** 업로드 파일 저장소 — 로컬 디스크·GCS, 또는 업로드를 끈 상태. 구현은 bookey.storage.type 으로 고른다. */
public interface StorageService {

    /**
     * 저장소가 실제로 파일을 받을 수 있는지. 꺼져 있으면(false) 업로드 API 가 파일을 읽기 전에 거절한다 —
     * 10MB 를 다 받아 놓고 버리지 않기 위함이다. 저장할 수 있는 구현은 그대로 true 를 쓰면 된다.
     */
    default boolean enabled() {
        return true;
    }

    /** 저장 후 공개 읽기 URL 을 돌려준다. 실패는 ApiException(STORAGE_ERROR). */
    String store(String key, InputStream in, long size, String contentType);

    /** 없는 키는 무시한다. 키가 저장소 규칙에 어긋나면 ApiException(STORAGE_ERROR) 을 던질 수 있다. */
    void delete(String key);
}
