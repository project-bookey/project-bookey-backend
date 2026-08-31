package app.bookey.api.auth;

import app.bookey.domain.user.AuthProvider;

/** 소셜 provider 로부터 검증된 사용자 식별 정보. */
public record SocialProfile(
        AuthProvider provider,
        String providerUid,
        String email,
        String nickname,
        String avatarUrl
) {}
