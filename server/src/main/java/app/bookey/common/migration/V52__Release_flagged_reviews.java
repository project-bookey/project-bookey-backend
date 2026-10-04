package app.bookey.common.migration;

import app.bookey.domain.book.GenreKey;
import lombok.extern.slf4j.Slf4j;
import org.flywaydb.core.api.migration.BaseJavaMigration;
import org.flywaydb.core.api.migration.Context;
import org.springframework.stereotype.Component;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.TreeMap;

/**
 * V52 — 어뷰징 감지를 걷어낸 뒤(2026-10-05, 사용자 결정) 예전에 '의심(FLAGGED)'으로 묶인 리뷰를 풀어 준다.
 *
 * <p>순간 완독·하루 대량 완독 판정과 세션 플래그 제외 없이, 리뷰를 쓴 때까지의 독서 기록으로 등급을 다시 매긴다 —
 * 지금의 {@code VerificationService} 와 같은 규칙(커버리지 0.9 · 타이머 3회 · 최소 시간 → 완독 확인,
 * 커버리지 0.5 · 타이머 2회 → 일부 확인, 그 밖은 확인 전). 규칙 값은 이 시점 그대로 여기에 고정한다 — 서비스 규칙이
 * 나중에 바뀌어도 이 마이그레이션의 결과는 바뀌지 않게. 예전 스냅숏은 새 스냅숏의 {@code previous} 에 남긴다.
 * 읽기 기록이 지워진 리뷰는 다시 잴 수 없어 확인 전으로 둔다.
 *
 * <p>JDBC 만 쓴다({@link V36__Remove_book_quotes} 와 같은 까닭). <b>이 클래스는 지우면 안 된다</b> — 이미 적용된
 * 버전을 Flyway 가 찾지 못하면 서버가 뜨지 않는다.
 */
@Slf4j
@Component
public class V52__Release_flagged_reviews extends BaseJavaMigration {

    private static final double MINUTES_PER_PAGE = 0.7;
    private static final double SPEED_ALLOWANCE = 0.35;
    private static final double FULL_COVERAGE = 0.9;
    private static final int FULL_TIMER_SESSIONS = 3;
    private static final double PARTIAL_COVERAGE = 0.5;
    private static final int PARTIAL_TIMER_SESSIONS = 2;
    /** 수동 기록은 시간의 40%만 센다. */
    private static final double MANUAL_WEIGHT = 0.4;

    private static final String FIND_FLAGGED = """
            SELECT r.id, r.reading_record_id, r.created_at,
                   rr.total_pages_override, b.total_pages, b.genre_key
            FROM reviews r
            LEFT JOIN reading_records rr ON rr.id = r.reading_record_id
            LEFT JOIN books b ON b.id = rr.book_id
            WHERE r.verification_level = 'FLAGGED'
            ORDER BY r.id
            """;

    /** 리뷰를 쓴 때까지 시작한 독서 — 등급은 쓴 시점의 기록으로 고정하던 규칙을 따른다. */
    private static final String FIND_SESSIONS = """
            SELECT s.source, s.start_page, s.end_page, s.duration_sec, s.ended_at
            FROM reading_sessions s
            WHERE s.reading_record_id = ? AND s.started_at <= ?
            """;

    private static final String UPDATE_REVIEW = """
            UPDATE reviews
            SET verification_level = ?,
                verification_snapshot = ?::jsonb || jsonb_build_object('previous', verification_snapshot)
            WHERE id = ?
            """;

    private record Flagged(long id, Long recordId, Instant createdAt, int totalPages, double genreCoefficient) {}

    private record Measure(double coverage, int timerSessions, long verifiedMinutes, long requiredMinutes) {}

    @Override
    public void migrate(Context context) throws Exception {
        Connection connection = context.getConnection();
        List<Flagged> flagged = findFlagged(connection);
        Map<String, Integer> released = new TreeMap<>();
        try (PreparedStatement update = connection.prepareStatement(UPDATE_REVIEW)) {
            for (Flagged review : flagged) {
                Measure measure = review.recordId() == null
                        ? new Measure(0, 0, 0, 0)
                        : measure(connection, review);
                String level = review.recordId() == null ? "UNVERIFIED" : decide(measure);
                update.setString(1, level);
                update.setString(2, snapshot(measure));
                update.setLong(3, review.id());
                update.addBatch();
                released.merge(level, 1, Integer::sum);
            }
            update.executeBatch();
        }
        log.info("의심 리뷰 풀기 — {}건 다시 매김 {}", flagged.size(), released);
    }

    private List<Flagged> findFlagged(Connection connection) throws SQLException {
        List<Flagged> flagged = new ArrayList<>();
        try (PreparedStatement select = connection.prepareStatement(FIND_FLAGGED);
             ResultSet rows = select.executeQuery()) {
            while (rows.next()) {
                long recordId = rows.getLong("reading_record_id");
                Long record = rows.wasNull() ? null : recordId;
                int override = rows.getInt("total_pages_override");
                boolean hasOverride = !rows.wasNull() && override > 0;
                int bookPages = rows.getInt("total_pages");
                flagged.add(new Flagged(
                        rows.getLong("id"),
                        record,
                        rows.getTimestamp("created_at").toInstant(),
                        hasOverride ? override : bookPages,
                        coefficient(rows.getString("genre_key"))));
            }
        }
        return flagged;
    }

    private Measure measure(Connection connection, Flagged review) throws SQLException {
        List<int[]> ranges = new ArrayList<>();
        int timerSessions = 0;
        long verifiedSeconds = 0;
        try (PreparedStatement select = connection.prepareStatement(FIND_SESSIONS)) {
            select.setLong(1, review.recordId());
            select.setTimestamp(2, Timestamp.from(review.createdAt()));
            try (ResultSet rows = select.executeQuery()) {
                while (rows.next()) {
                    boolean timer = "TIMER".equals(rows.getString("source"));
                    if (timer) {
                        timerSessions++;
                    }
                    // 리뷰를 쓸 때 아직 열려 있던 독서는 그때 쪽수·시간이 없었다.
                    Timestamp endedAt = rows.getTimestamp("ended_at");
                    if (endedAt == null || endedAt.toInstant().isAfter(review.createdAt())) {
                        continue;
                    }
                    verifiedSeconds += Math.round(rows.getInt("duration_sec") * (timer ? 1.0 : MANUAL_WEIGHT));
                    int start = rows.getInt("start_page");
                    boolean hasStart = !rows.wasNull();
                    int end = rows.getInt("end_page");
                    boolean hasEnd = !rows.wasNull();
                    if (hasStart && hasEnd && end > start) {
                        ranges.add(new int[]{start, end});
                    }
                }
            }
        }
        int totalPages = review.totalPages();
        double coverage = totalPages > 0 ? Math.min(1.0, (double) mergeUniquePages(ranges) / totalPages) : 0;
        long requiredMinutes = totalPages <= 0
                ? 0
                : Math.round(totalPages * MINUTES_PER_PAGE * SPEED_ALLOWANCE * review.genreCoefficient());
        return new Measure(coverage, timerSessions, verifiedSeconds / 60, requiredMinutes);
    }

    private static String decide(Measure m) {
        if (m.coverage() >= FULL_COVERAGE
                && m.timerSessions() >= FULL_TIMER_SESSIONS
                && m.verifiedMinutes() >= m.requiredMinutes()) {
            return "VERIFIED_FULL";
        }
        if (m.coverage() >= PARTIAL_COVERAGE && m.timerSessions() >= PARTIAL_TIMER_SESSIONS) {
            return "VERIFIED_PARTIAL";
        }
        return "UNVERIFIED";
    }

    /** 겹치는 쪽 구간은 한 번만 센다. */
    private static long mergeUniquePages(List<int[]> ranges) {
        ranges.sort(Comparator.comparingInt(r -> r[0]));
        long total = 0;
        int start = -1;
        int end = -1;
        for (int[] range : ranges) {
            if (start < 0) {
                start = range[0];
                end = range[1];
            } else if (range[0] <= end) {
                end = Math.max(end, range[1]);
            } else {
                total += end - start;
                start = range[0];
                end = range[1];
            }
        }
        return start < 0 ? total : total + (end - start);
    }

    private static double coefficient(String genreKey) {
        try {
            return genreKey == null ? 1.0 : GenreKey.valueOf(genreKey).getCoefficient();
        } catch (IllegalArgumentException unknown) {
            return 1.0;
        }
    }

    private static String snapshot(Measure m) {
        return String.format(Locale.ROOT,
                "{\"coverage\":%s,\"timerSessionCount\":%d,\"verifiedMinutes\":%d,\"requiredMinutes\":%d,"
                        + "\"evaluatedAt\":\"%s\",\"reevaluatedBy\":\"V52 어뷰징 감지 제거\"}",
                Math.round(m.coverage() * 1000) / 1000.0, m.timerSessions(), m.verifiedMinutes(),
                m.requiredMinutes(), Instant.now());
    }
}
