package app.bookey.api.club;

import app.bookey.api.club.dto.ClubDtos.ClubSeatResult;
import app.bookey.api.club.dto.ClubDtos.ExpandSeatsRequest;
import app.bookey.api.social.WalletService;
import app.bookey.common.config.BookeyProperties;
import app.bookey.common.error.ApiException;
import app.bookey.common.error.ErrorCode;
import app.bookey.common.support.RateLimiter;
import app.bookey.domain.club.Club;
import app.bookey.domain.club.ClubRepository;
import app.bookey.domain.wallet.Wallet;
import app.bookey.domain.wallet.WalletTransactionKind;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;

/**
 * 모임 자리 늘리기. 무료 정원을 넘는 자리는 호스트가 책갈피로 연다.
 * 늘린 자리는 그 모임에만 속한다 — 환불·이월·양도 경로를 두지 않는다.
 */
@Service
@RequiredArgsConstructor
public class ClubSeatService {

    private static final int SEAT_RATE_LIMIT_PER_HOUR = 10;
    static final String LEDGER_REF_TYPE = "CLUB";

    private final ClubRepository clubRepository;
    private final WalletService walletService;
    private final RateLimiter rateLimiter;
    private final BookeyProperties properties;

    @Transactional
    public ClubSeatResult expand(Long userId, Long clubId, ExpandSeatsRequest request) {
        rateLimiter.require("club:seat:" + userId, SEAT_RATE_LIMIT_PER_HOUR, Duration.ofHours(1));

        // 락 순서는 모임 → 지갑으로 고정한다. 참가도 모임 락을 잡으므로 순서가 엇갈리면 교착이 난다.
        Club club = clubRepository.findByIdForUpdate(clubId)
                .orElseThrow(() -> ApiException.of(ErrorCode.CLUB_NOT_FOUND));
        if (!club.isHost(userId)) {
            throw ApiException.of(ErrorCode.CLUB_NOT_HOST);
        }

        BookeyProperties.Club policy = properties.club();
        int added = club.expandMemberLimit(request.targetLimit(), policy.maxMemberLimit());
        // 책갈피가 모자라면 예외로 트랜잭션 전체가 롤백되어 정원도 되돌아간다.
        Wallet wallet = walletService.spendBookmarks(userId, added * policy.seatCostBookmarks(),
                WalletTransactionKind.CLUB_SEAT, LEDGER_REF_TYPE, clubId);

        return new ClubSeatResult(club.getMemberLimit(), wallet.getBookmarkBalance());
    }
}
