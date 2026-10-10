package app.bookey.domain.appconfig;

import app.bookey.domain.user.DevicePlatform;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AppReleaseConfigRepository extends JpaRepository<AppReleaseConfig, DevicePlatform> {
}
