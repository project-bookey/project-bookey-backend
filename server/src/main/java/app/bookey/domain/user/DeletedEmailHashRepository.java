package app.bookey.domain.user;

import org.springframework.data.jpa.repository.JpaRepository;

public interface DeletedEmailHashRepository extends JpaRepository<DeletedEmailHash, String> {
}
