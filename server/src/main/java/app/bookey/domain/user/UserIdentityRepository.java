package app.bookey.domain.user;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface UserIdentityRepository extends JpaRepository<UserIdentity, Long> {

    Optional<UserIdentity> findByProviderAndProviderUid(AuthProvider provider, String providerUid);

    List<UserIdentity> findAllByUserId(Long userId);
}
