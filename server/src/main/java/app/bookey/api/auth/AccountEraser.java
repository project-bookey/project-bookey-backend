package app.bookey.api.auth;

import app.bookey.common.storage.StorageKeys;
import app.bookey.common.storage.StorageService;
import app.bookey.common.support.AfterCommit;
import app.bookey.domain.notification.NotificationRepository;
import app.bookey.domain.social.ProfileVisitRepository;
import app.bookey.domain.user.User;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * 탈퇴 때 다른 회원과 나누지 않는 기록을 지운다 — 사용자 행은 익명화만 하므로 FK CASCADE 가 일어나지 않는다.
 * 알림, 프로필 방문 기록(내가 간 곳·나를 찾은 기록), 업로드한 프로필 사진 파일.
 * 공개 게시물·대화처럼 상대가 있는 기록은 익명화된 작성자로 남긴다(개인정보처리방침의 파기 항목과 같다).
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class AccountEraser {

    private final NotificationRepository notificationRepository;
    private final ProfileVisitRepository profileVisitRepository;
    private final StorageService storage;

    /** 익명화 전에 부른다 — 아바타 URL 이 아직 남아 있어야 파일 키를 안다. */
    public void erase(User user) {
        Long userId = user.getId();
        notificationRepository.deleteAllByUserId(userId);
        profileVisitRepository.deleteAllInvolving(userId);
        String avatarKey = StorageKeys.avatarKeyOf(userId, user.getAvatarUrl());
        if (avatarKey != null) {
            AfterCommit.run(() -> deleteQuietly(avatarKey));
        }
    }

    private void deleteQuietly(String key) {
        try {
            storage.delete(key);
        } catch (Exception e) {
            log.warn("탈퇴 회원 프로필 사진 삭제 실패(손으로 회수): key={}", key, e);
        }
    }
}
