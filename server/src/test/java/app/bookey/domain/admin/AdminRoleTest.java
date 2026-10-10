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

    @Test
    @DisplayName("배너·공지·에디터 픽은 운영자까지, 결제 열람은 CS 까지, 관리자 계정·푸시 발송은 최고 관리자만")
    void contentPaymentsAdmins() {
        assertThat(AdminRole.OPERATOR.canManageContent()).isTrue();
        assertThat(AdminRole.SUPPORT.canManageContent()).isFalse();
        assertThat(AdminRole.SUPPORT.canViewPayments()).isTrue();
        assertThat(AdminRole.VIEWER.canViewPayments()).isFalse();
        assertThat(AdminRole.OPERATOR.canManageAdmins()).isFalse();
        assertThat(AdminRole.OPERATOR.canBroadcast()).isFalse();
        assertThat(AdminRole.SUPER_ADMIN.canManageAdmins()).isTrue();
    }

    @Test
    @DisplayName("도서 병합은 되돌릴 수 없어 최고 관리자만 — 도서 수정이 되는 운영자도 못 한다")
    void mergeBooks() {
        assertThat(AdminRole.SUPER_ADMIN.canMergeBooks()).isTrue();
        assertThat(AdminRole.OPERATOR.canEditBook()).isTrue();
        assertThat(AdminRole.OPERATOR.canMergeBooks()).isFalse();
        assertThat(AdminRole.OPERATOR.capabilities()).doesNotContain(AdminCapability.MERGE_BOOKS);
        assertThat(AdminRole.SUPER_ADMIN.capabilities()).contains(AdminCapability.MERGE_BOOKS);
    }

    @Test
    @DisplayName("capabilities — 역할 판정과 같은 목록을 웹에 내려준다")
    void capabilitiesMatchRole() {
        assertThat(AdminRole.VIEWER.capabilities()).isEmpty();
        assertThat(AdminRole.SUPPORT.capabilities()).containsExactly(
                AdminCapability.WARN, AdminCapability.HANDLE_SUPPORT, AdminCapability.VIEW_PAYMENTS);
        assertThat(AdminRole.SUPER_ADMIN.capabilities()).containsExactlyInAnyOrder(AdminCapability.values());
    }
}
