package app.bookey.common.storage;

import java.io.InputStream;

/** 업로드 파일 저장소 — 로컬 디스크 또는 GCS. 구현은 bookey.storage.type 으로 고른다. */
public interface StorageService {

    /** 저장 후 공개 읽기 URL 을 돌려준다. 실패는 ApiException(STORAGE_ERROR). */
    String store(String key, InputStream in, long size, String contentType);

    /** 없는 키는 조용히 무시한다. */
    void delete(String key);
}
