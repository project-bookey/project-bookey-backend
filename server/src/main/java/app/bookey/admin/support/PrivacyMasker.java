package app.bookey.admin.support;

/** 관리자 화면 기본 마스킹 (§F13). "전체 보기"는 사유 입력 후 1회성으로만 허용한다. */
public final class PrivacyMasker {

    private PrivacyMasker() {}

    public static String email(String email) {
        if (email == null || email.isBlank()) {
            return null;
        }
        int at = email.indexOf('@');
        if (at <= 1) {
            return "***" + (at < 0 ? "" : email.substring(at));
        }
        String local = email.substring(0, at);
        String visible = local.substring(0, Math.min(2, local.length()));
        return visible + "***" + email.substring(at);
    }

    public static String nickname(String nickname) {
        if (nickname == null || nickname.length() <= 1) {
            return nickname;
        }
        return nickname.charAt(0) + "*".repeat(nickname.length() - 1);
    }
}
