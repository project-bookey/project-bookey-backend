package app.bookey.domain.user;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface UserDeviceRepository extends JpaRepository<UserDevice, Long> {

    Optional<UserDevice> findByPlatformAndPushToken(DevicePlatform platform, String pushToken);

    List<UserDevice> findAllByUserIdAndPushEnabledTrue(Long userId);

    /** 관리자 회원 상세 — 푸시를 끈 기기까지 최근 접속 순으로. */
    List<UserDevice> findAllByUserIdOrderByLastSeenAtDesc(Long userId);

    void deleteAllByUserId(Long userId);
}
