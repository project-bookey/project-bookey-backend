package app.bookey.batch;

import app.bookey.admin.AdminUserService;
import app.bookey.domain.user.User;
import app.bookey.domain.user.UserRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.util.List;

import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class SanctionExpiryJobTest {

    private final UserRepository userRepository = mock(UserRepository.class);
    private final AdminUserService adminUserService = mock(AdminUserService.class);
    private final SanctionExpiryJob job = new SanctionExpiryJob(userRepository, adminUserService);

    private static User user(long id) {
        User user = User.builder().handle("r" + id).email("r@dev.local").nickname("독자" + id).build();
        try {
            Field f = User.class.getDeclaredField("id");
            f.setAccessible(true);
            f.set(user, id);
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException(e);
        }
        return user;
    }

    @Test
    @DisplayName("한 회원이 실패해도 나머지 회원은 계속 처리한다")
    void continuesAfterFailure() {
        when(userRepository.findAllByStatusInAndDeletionRequestedAtIsNull(anyList()))
                .thenReturn(List.of(user(1L), user(2L)));
        when(adminUserService.reconcileStatus(1L)).thenThrow(new IllegalStateException("boom"));

        job.releaseExpired();

        verify(adminUserService).reconcileStatus(2L);
    }
}
