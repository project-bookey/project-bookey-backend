package app.bookey.domain.user;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface UserDeviceRepository extends JpaRepository<UserDevice, Long> {

    Optional<UserDevice> findByPlatformAndPushToken(DevicePlatform platform, String pushToken);

    List<UserDevice> findAllByUserIdAndPushEnabledTrue(Long userId);
}
