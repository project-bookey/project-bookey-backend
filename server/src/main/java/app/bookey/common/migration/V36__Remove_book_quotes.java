package app.bookey.common.migration;

import app.bookey.domain.post.LegacyQuoteInliner;
import app.bookey.domain.post.LegacyQuoteInliner.Quote;
import lombok.extern.slf4j.Slf4j;
import org.flywaydb.core.api.migration.BaseJavaMigration;
import org.flywaydb.core.api.migration.Context;
import org.springframework.stereotype.Component;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;

/**
 * V36 — 밑줄(오려둔 문장) 기능을 걷어내며 그 데이터를 지운다.
 *
 * <ol>
 *   <li>글 독후감(TEXT)에 엮인 밑줄은 지우기 전에 본문 글로 옮긴다({@link LegacyQuoteInliner}) — 글쓴이가 쓴 글의
 *       일부라서. 출처는 책 제목 · 쪽수만 남기고 남의 닉네임은 넣지 않는다. 노트 독후감의 문장 조각은 문서 안
 *       스냅숏이라 밑줄을 지워도 그대로 남는다.</li>
 *   <li>밑줄 알림(QUOTE_AGREED · QUOTE_COMMENTED)을 지운다 — 가리키던 밑줄이 사라지고 알림 종류에서도 빠졌다.</li>
 *   <li>밑줄 테이블을 지운다 — post_quotes · quote_comments · quote_agrees · book_quotes, 그리고 쓰지 않던 옛 quotes(V1).</li>
 * </ol>
 *
 * <p>SQL 이 아니라 자바 마이그레이션인 까닭: 표시를 조각 글로 바꾸는 규칙(빈 줄 정리·출처 줄)을 앱과 똑같이 맞추려면
 * 단위 테스트한 순수 규칙을 그대로 써야 한다. 스프링 빈으로 두면 Flyway 자동설정이 JavaMigration 빈을 가져다 쓴다
 * ({@code db/migration} 스캔에는 걸리지 않아 한 번만 등록된다). JDBC 만 쓴다 — 리포지토리·엔티티는 스키마 검증
 * 전이라 쓰면 안 된다. Flyway 가 한 트랜잭션으로 돌리므로 중간에 실패하면 본문 이동까지 통째로 되돌아간다.
 *
 * <p><b>이 클래스와 {@link LegacyQuoteInliner} 는 지우면 안 된다</b> — 이미 적용된 버전을 Flyway 가 찾지 못하면
 * 서버가 뜨지 않는다. 새 SQL 마이그레이션 번호는 V37 부터다(CLAUDE.md 'DB 마이그레이션').
 */
@Slf4j
@Component
public class V36__Remove_book_quotes extends BaseJavaMigration {

    /** 옛 표시가 있거나 밑줄이 엮인 글 독후감. */
    private static final String FIND_POSTS = """
            SELECT p.id, p.body_md
            FROM posts p
            WHERE p.format = 'TEXT'
              AND (p.body_md LIKE ? OR EXISTS (SELECT 1 FROM post_quotes pq WHERE pq.post_id = p.id))
            ORDER BY p.id
            """;

    /** 글에 엮인 밑줄 — 붙인 순서대로. 책이 없는 밑줄은 예전 화면에서도 빠졌으니 함께 뺀다(INNER JOIN). */
    private static final String FIND_QUOTES = """
            SELECT q.id, b.title, q.page, q.content
            FROM post_quotes pq
            JOIN book_quotes q ON q.id = pq.quote_id
            JOIN books b ON b.id = q.book_id
            WHERE pq.post_id = ?
            ORDER BY pq.sort_order, pq.id
            """;

    @Override
    public void migrate(Context context) throws Exception {
        Connection connection = context.getConnection();
        try (Statement statement = connection.createStatement()) {
            // 남은 세션이 테이블을 붙들고 있으면 기동이 하염없이 멈추지 않고 실패하게 한다(트랜잭션 안에서만 유효).
            statement.execute("SET LOCAL lock_timeout = '30s'");
        }
        int moved = inlineQuotesIntoPosts(connection);
        try (Statement statement = connection.createStatement()) {
            int notifications = statement.executeUpdate(
                    "DELETE FROM notifications WHERE type IN ('QUOTE_AGREED', 'QUOTE_COMMENTED')");
            statement.execute("DROP TABLE post_quotes");
            statement.execute("DROP TABLE quote_comments");
            statement.execute("DROP TABLE quote_agrees");
            statement.execute("DROP TABLE book_quotes");
            statement.execute("DROP TABLE IF EXISTS quotes");
            log.info("밑줄 걷어내기 — 본문으로 옮긴 독후감 {}건, 지운 밑줄 알림 {}건, 밑줄 테이블 삭제", moved, notifications);
        }
    }

    /** 옛 표시·엮인 밑줄을 본문 글로 옮기고 바뀐 글 수를 돌려준다. */
    private int inlineQuotesIntoPosts(Connection connection) throws SQLException {
        List<Long> ids = new ArrayList<>();
        List<String> bodies = new ArrayList<>();
        try (PreparedStatement find = connection.prepareStatement(FIND_POSTS)) {
            find.setString(1, "%〖오려둔 문장 %");
            try (ResultSet rows = find.executeQuery()) {
                while (rows.next()) {
                    ids.add(rows.getLong("id"));
                    bodies.add(rows.getString("body_md"));
                }
            }
        }
        int changed = 0;
        try (PreparedStatement quotes = connection.prepareStatement(FIND_QUOTES);
             PreparedStatement update = connection.prepareStatement("UPDATE posts SET body_md = ? WHERE id = ?")) {
            for (int i = 0; i < ids.size(); i++) {
                long postId = ids.get(i);
                String body = bodies.get(i);
                String inlined = LegacyQuoteInliner.inline(body, loadQuotes(quotes, postId));
                if (inlined != null && !inlined.equals(body)) {
                    update.setString(1, inlined);
                    update.setLong(2, postId);
                    update.executeUpdate();
                    changed++;
                }
            }
        }
        return changed;
    }

    private List<Quote> loadQuotes(PreparedStatement statement, long postId) throws SQLException {
        statement.setLong(1, postId);
        List<Quote> quotes = new ArrayList<>();
        try (ResultSet rows = statement.executeQuery()) {
            while (rows.next()) {
                // wasNull 은 바로 앞에 읽은 칸을 본다 — 쪽을 읽자마자 판정한다.
                int pageValue = rows.getInt("page");
                Integer page = rows.wasNull() ? null : pageValue;
                quotes.add(new Quote(rows.getLong("id"), rows.getString("title"), page, rows.getString("content")));
            }
        }
        return quotes;
    }
}
