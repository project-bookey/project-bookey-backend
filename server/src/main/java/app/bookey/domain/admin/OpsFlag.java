package app.bookey.domain.admin;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.Instant;

/** 운영 스위치. PUSH_ENABLED 는 긴급 킬스위치(§F13 알림 운영). */
@Getter
@Entity
@Table(name = "ops_flags")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class OpsFlag {

    public static final String PUSH_ENABLED = "PUSH_ENABLED";
    public static final String CLUB_CREATION_OPEN = "CLUB_CREATION_OPEN";
    public static final String SIGNUP_OPEN = "SIGNUP_OPEN";

    @Id
    @Column(name = "key", length = 50)
    private String key;

    @Column(nullable = false)
    private boolean enabled = true;

    @Column(length = 300)
    private String note;

    @Column(name = "updated_by")
    private Long updatedBy;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt = Instant.now();

    public void change(boolean enabled, Long adminId, String note) {
        this.enabled = enabled;
        this.updatedBy = adminId;
        this.note = note;
        this.updatedAt = Instant.now();
    }
}
