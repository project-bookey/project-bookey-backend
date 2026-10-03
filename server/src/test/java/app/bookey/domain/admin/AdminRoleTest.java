package app.bookey.domain.admin;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class AdminRoleTest {

    @Test
    @DisplayName("고객문의 답변·FAQ 편집은 보기 전용(VIEWER)만 못 한다")
    void canHandleSupport() {
        assertThat(AdminRole.SUPER_ADMIN.canHandleSupport()).isTrue();
        assertThat(AdminRole.OPERATOR.canHandleSupport()).isTrue();
        assertThat(AdminRole.SUPPORT.canHandleSupport()).isTrue();
        assertThat(AdminRole.VIEWER.canHandleSupport()).isFalse();
    }
}
