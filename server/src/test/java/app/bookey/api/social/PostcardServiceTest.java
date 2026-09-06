package app.bookey.api.social;

import app.bookey.api.social.dto.SocialDtos.PostcardView;
import app.bookey.api.social.dto.SocialDtos.ReplyPostcardRequest;
import app.bookey.api.social.dto.SocialDtos.SendPostcardRequest;
import app.bookey.common.config.BookeyProperties;
import app.bookey.common.error.ApiException;
import app.bookey.common.error.ErrorCode;
import app.bookey.domain.post.PostRepository;
import app.bookey.domain.social.FollowSource;
import app.bookey.domain.social.Postcard;
import app.bookey.domain.social.PostcardRepository;
import app.bookey.domain.social.PostcardStatus;
import app.bookey.domain.user.User;
import app.bookey.domain.user.UserRepository;
import app.bookey.domain.wallet.Wallet;
import app.bookey.domain.wallet.WalletTransactionKind;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 엽서 계약 단위 테스트 (§14.2) — 16글자 제한, 자기 자신·중복 발송 금지,
 * 우표 동봉·답장 지불, 답장 성립 시 자동 맞팔로우.
 */
class PostcardServiceTest {

    private static final BookeyProperties.Social SOCIAL =
            new BookeyProperties.Social(5, 16, 1, 2, 50, 30, 17900);

    private final PostcardRepository postcardRepository = mock(PostcardRepository.class);
    private final UserRepository userRepository = mock(UserRepository.class);
    private final PostRepository postRepository = mock(PostRepository.class);
    private final WalletService walletService = mock(WalletService.class);
    private final FollowService followService = mock(FollowService.class);
    private final Clock clock = mock(Clock.class);
    private final BookeyProperties properties =
            new BookeyProperties(null, null, null, null, null, null, SOCIAL, null);
    private final PostcardService service = new PostcardService(
            postcardRepository, userRepository, postRepository, walletService, followService, properties, clock);

    private final Wallet wallet = new Wallet(1L, LocalDate.of(2026, 9, 6));

    private void stubBasics() {
        when(clock.instant()).thenReturn(Instant.parse("2026-09-06T03:00:00Z"));
        User to = User.builder().handle("reader2").email("to@dev.local").nickname("수신자").build();
        set(to, "id", 2L);
        when(userRepository.findById(2L)).thenReturn(Optional.of(to));
        when(userRepository.findAllById(any())).thenReturn(List.of(to));
        when(walletService.prepared(1L)).thenReturn(wallet);
        when(postcardRepository.save(any(Postcard.class))).thenAnswer(inv -> {
            Postcard card = inv.getArgument(0);
            set(card, "id", 100L);
            return card;
        });
    }

    private static void set(Object target, String field, Object value) {
        Class<?> type = target.getClass();
        while (type != null) {
            try {
                Field f = type.getDeclaredField(field);
                f.setAccessible(true);
                f.set(target, value);
                return;
            } catch (NoSuchFieldException e) {
                type = type.getSuperclass();
            } catch (Exception e) {
                throw new IllegalStateException(e);
            }
        }
        throw new IllegalStateException("필드를 찾을 수 없습니다: " + field);
    }

    private static void assertApiError(Runnable call, ErrorCode expected) {
        assertThatThrownBy(call::run)
                .isInstanceOf(ApiException.class)
                .extracting(e -> ((ApiException) e).getErrorCode())
                .isEqualTo(expected);
    }

    // ───────────── 발송 ─────────────

    @Test
    @DisplayName("발송 — 16글자 이내면 저장하고 발송 비용을 지불한다")
    void sendWithinLimit() {
        stubBasics();

        PostcardView view = service.send(1L,
                new SendPostcardRequest(2L, null, "가나다라마바사아자차카타파하기니", false));

        assertThat(view.body()).isEqualTo("가나다라마바사아자차카타파하기니");
        assertThat(view.status()).isEqualTo(PostcardStatus.SENT);
        assertThat(view.mine()).isTrue();
        verify(walletService).payPostcardSend(eq(1L), eq(wallet), eq(100L));
        verify(walletService, never()).payStamp(anyLong(), any(), any(), anyLong());
    }

    @Test
    @DisplayName("발송 — 17글자는 POSTCARD_BODY_TOO_LONG (한글 완성형 기준, §14.9)")
    void sendTooLong() {
        stubBasics();

        assertApiError(() -> service.send(1L,
                new SendPostcardRequest(2L, null, "가나다라마바사아자차카타파하기니스", false)),
                ErrorCode.POSTCARD_BODY_TOO_LONG);
        verify(postcardRepository, never()).save(any());
    }

    @Test
    @DisplayName("발송 — 나에게 보내면 POSTCARD_SELF")
    void sendToSelf() {
        assertApiError(() -> service.send(1L,
                new SendPostcardRequest(1L, null, "안녕", false)), ErrorCode.POSTCARD_SELF);
    }

    @Test
    @DisplayName("발송 — 같은 상대에게 답장 대기 엽서가 있으면 POSTCARD_ALREADY_SENT")
    void sendDuplicatePending() {
        stubBasics();
        when(postcardRepository.existsByFromUserIdAndToUserIdAndStatus(1L, 2L, PostcardStatus.SENT))
                .thenReturn(true);

        assertApiError(() -> service.send(1L,
                new SendPostcardRequest(2L, null, "안녕", false)), ErrorCode.POSTCARD_ALREADY_SENT);
    }

    @Test
    @DisplayName("발송 — 우표 동봉이면 발신자 우표를 먼저 차감한다 (ATTACH_STAMP)")
    void sendWithStampAttached() {
        stubBasics();

        PostcardView view = service.send(1L, new SendPostcardRequest(2L, null, "부담없이 답장해요", true));

        assertThat(view.stampAttached()).isTrue();
        verify(walletService).payStamp(eq(1L), eq(wallet), eq(WalletTransactionKind.ATTACH_STAMP), eq(100L));
        verify(walletService).payPostcardSend(eq(1L), eq(wallet), eq(100L));
    }

    @Test
    @DisplayName("발송 — postId 컨텍스트는 수신자의 글이어야 한다")
    void sendWithForeignPostContext() {
        stubBasics();
        app.bookey.domain.post.Post post = app.bookey.domain.post.Post.builder()
                .userId(99L).slug("s").title("남의 글").bodyMd("...")
                .visibility(app.bookey.domain.post.PostVisibility.PUBLIC).build();
        when(postRepository.findById(50L)).thenReturn(Optional.of(post));

        assertApiError(() -> service.send(1L,
                new SendPostcardRequest(2L, 50L, "안녕", false)), ErrorCode.INVALID_REQUEST);
    }

    // ───────────── 답장 ─────────────

    private Postcard sentCard(boolean stampAttached) {
        Postcard card = Postcard.builder()
                .fromUserId(1L).toUserId(2L).body("첫 엽서").stampAttached(stampAttached).build();
        set(card, "id", 100L);
        when(postcardRepository.findById(100L)).thenReturn(Optional.of(card));
        return card;
    }

    @Test
    @DisplayName("답장 — 우표 1개를 차감하고 REPLIED 로 바꾸며 자동 맞팔로우된다")
    void replyConsumesStampAndFollows() {
        stubBasics();
        Postcard card = sentCard(false);
        Wallet replierWallet = new Wallet(2L, LocalDate.of(2026, 9, 6));
        when(walletService.prepared(2L)).thenReturn(replierWallet);

        service.reply(2L, 100L, new ReplyPostcardRequest("반가워요"));

        assertThat(card.isReplied()).isTrue();
        assertThat(card.getReplyBody()).isEqualTo("반가워요");
        verify(walletService).payStamp(eq(2L), eq(replierWallet), eq(WalletTransactionKind.REPLY_STAMP), eq(100L));
        verify(followService).ensureMutual(1L, 2L, FollowSource.POSTCARD);
    }

    @Test
    @DisplayName("답장 — 우표 동봉 엽서는 무료로 답장한다 (발신자가 이미 부담)")
    void replyToStampAttachedIsFree() {
        stubBasics();
        sentCard(true);

        service.reply(2L, 100L, new ReplyPostcardRequest("고마워요"));

        verify(walletService, never()).payStamp(anyLong(), any(), any(), anyLong());
        verify(followService).ensureMutual(1L, 2L, FollowSource.POSTCARD);
    }

    @Test
    @DisplayName("답장 — 수신자가 아니면 POSTCARD_NOT_FOUND (존재를 드러내지 않음), 이미 답장했으면 POSTCARD_ALREADY_REPLIED")
    void replyGuards() {
        stubBasics();
        Postcard card = sentCard(false);

        assertApiError(() -> service.reply(9L, 100L, new ReplyPostcardRequest("몰래")),
                ErrorCode.POSTCARD_NOT_FOUND);

        card.reply("이미 답장", Instant.parse("2026-09-06T02:00:00Z"));
        assertApiError(() -> service.reply(2L, 100L, new ReplyPostcardRequest("또")),
                ErrorCode.POSTCARD_ALREADY_REPLIED);
    }
}
