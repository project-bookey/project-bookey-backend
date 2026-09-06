package app.bookey.api.auth;

import java.time.LocalDate;

/** 본인인증 결과 — 인증사가 확인한 실명 정보. CI 는 사람당 1개라 중복 가입 차단 키가 된다. */
public record VerifiedIdentity(
        String name,
        String phone,
        LocalDate birthDate,
        String ci,
        String di
) {}
