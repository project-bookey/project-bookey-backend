package app.bookey.domain.user;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface UserDeviceRepository extends JpaRepository<UserDevice, Long> {

    Optional<UserDevice> findByPlatformAndPushToken(DevicePlatform platform, String pushToken);

    List<UserDevice> findAllByUserIdAndPushEnabledTrue(Long userId);

    /** 캠페인 묶음 발송 — 여러 회원의 기기를 한 번에. */
    List<UserDevice> findAllByUserIdInAndPushEnabledTrue(java.util.Collection<Long> userIds);

    /** 관리자 회원 상세 — 푸시를 끈 기기까지 최근 접속 순으로. */
    List<UserDevice> findAllByUserIdOrderByLastSeenAtDesc(Long userId);

    void deleteAllByUserId(Long userId);
}
