package app.bookey.api.auth;

import app.bookey.api.club.ClubService;
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
 * 탈퇴를 요청한 즉시 지우는 기록 — 사용자 행은 30일 유예기간 동안 익명화된 채 남으므로 FK CASCADE 를 기다리지 않는다.
 * 알림(내가 받은 것과, 내가 한 일로 남에게 간 것), 프로필 방문 기록(내가 간 곳·나를 찾은 기록),
 * 업로드한 프로필 사진 파일(저장소 파일은 CASCADE 로도 지워지지 않는다). 참가한 클럽은 모두 나간다(호스트 자리는 넘긴다).
 * 나머지 기록은 AccountDeletionJob 이 30일 뒤 사용자 행과 함께 지운다(개인정보처리방침의 파기 항목과 같다) —
 * 그때까지 글·리뷰·댓글·채팅·엽서 같은 기록은 다른 사람에게 보이지 않는다(계정 상태 TERMINATED 로 조회에서 뺀다).
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class AccountEraser {

    private final NotificationRepository notificationRepository;
    private final ProfileVisitRepository profileVisitRepository;
    private final ClubService clubService;
    private final StorageService storage;

    /** 익명화 전에 부른다 — 아바타 URL 이 아직 남아 있어야 파일 키를 안다. */
    public void erase(User user) {
        Long userId = user.getId();
        notificationRepository.deleteAllByUserId(userId);
        notificationRepository.deleteAllCausedBy(userId);
        profileVisitRepository.deleteAllInvolving(userId);
        clubService.leaveAllOnWithdrawal(userId);
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
