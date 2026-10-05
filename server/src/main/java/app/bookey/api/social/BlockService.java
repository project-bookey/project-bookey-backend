package app.bookey.api.social;

import app.bookey.api.social.dto.SocialDtos.BlockedUserView;
import app.bookey.common.error.ApiException;
import app.bookey.common.error.ErrorCode;
import app.bookey.common.support.PageResponse;
import app.bookey.domain.social.UserBlock;
import app.bookey.domain.social.UserBlockRepository;
import app.bookey.domain.user.User;
import app.bookey.domain.user.UserRepository;
import app.bookey.domain.user.UserStatus;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * 사용자 차단 (2026-10-05, 사용자 결정) — 엽서·채팅만 막는다. 광장의 독후감·댓글·팔로우는 그대로다.
 * 막힌 사람은 막은 사람에게 엽서·답장·채팅을 보낼 수 없고, 막은 사람의 엽서함·채팅 목록에서 둘 사이의 것이 빠진다.
 * 막힌 사람에게는 알리지 않고, 데이터는 지우지 않아 풀면 다시 보인다. 해제는 앱 설정의 '차단한 사람'에서 한다.
 */
@Service
@RequiredArgsConstructor
public class BlockService {

    private final UserBlockRepository blockRepository;
    private final UserRepository userRepository;
    private final Clock clock;

    /** 차단 — 이미 막았으면 그대로 성공. */
    @Transactional
    public BlockedUserView block(Long userId, Long targetId) {
        if (userId.equals(targetId)) {
            throw new ApiException(ErrorCode.INVALID_REQUEST, "나 자신은 차단할 수 없어요.");
        }
        User target = userRepository.findById(targetId)
                .filter(found -> found.getStatus() != UserStatus.TERMINATED)
                .orElseThrow(() -> ApiException.of(ErrorCode.NOT_FOUND));
        UserBlock block = blockRepository.findByBlockerIdAndBlockedId(userId, targetId)
                .orElseGet(() -> blockRepository.save(UserBlock.of(userId, targetId)));
        return toView(block, target);
    }

    /** 차단 풀기 — 막지 않았어도 그대로 성공. */
    @Transactional
    public void unblock(Long userId, Long targetId) {
        blockRepository.findByBlockerIdAndBlockedId(userId, targetId).ifPresent(blockRepository::delete);
    }

    @Transactional(readOnly = true)
    public PageResponse<BlockedUserView> list(Long userId, Pageable pageable) {
        Page<UserBlock> page = blockRepository.findAllMine(userId, pageable);
        Map<Long, User> users = userRepository.findAllById(
                        page.getContent().stream().map(UserBlock::getBlockedId).toList())
                .stream().collect(Collectors.toMap(User::getId, Function.identity()));
        return PageResponse.of(page, block -> toView(block, users.get(block.getBlockedId())));
    }

    /** 내가 그 사람을 막았는가 — 막은 쪽의 목록·방에서 둘 사이의 것을 뺄 때 쓴다. */
    @Transactional(readOnly = true)
    public boolean hasBlocked(Long userId, Long otherUserId) {
        return blockRepository.existsByBlockerIdAndBlockedId(userId, otherUserId);
    }

    /** 둘 중 누구든 막았는가 — 채팅을 새로 열 수 있는지 판정할 때 쓴다. */
    @Transactional(readOnly = true)
    public boolean blockedEitherWay(Long a, Long b) {
        return blockRepository.existsByBlockerIdAndBlockedId(a, b) || blockRepository.existsByBlockerIdAndBlockedId(b, a);
    }

    /**
     * 보내기 전 확인 — 엽서·답장·채팅 메시지. 내가 막았으면 풀 곳을 알려 주고(USER_BLOCKED),
     * 상대가 막았으면 막았다는 말은 하지 않는다(USER_UNREACHABLE).
     */
    @Transactional(readOnly = true)
    public void requireReachable(Long senderId, Long recipientId) {
        if (blockRepository.existsByBlockerIdAndBlockedId(senderId, recipientId)) {
            throw ApiException.of(ErrorCode.USER_BLOCKED);
        }
        if (blockRepository.existsByBlockerIdAndBlockedId(recipientId, senderId)) {
            throw ApiException.of(ErrorCode.USER_UNREACHABLE);
        }
    }

    private BlockedUserView toView(UserBlock block, User user) {
        return new BlockedUserView(
                block.getBlockedId(),
                user == null ? "알 수 없음" : user.getNickname(),
                user == null ? null : user.getAvatarUrl(),
                block.getCreatedAt() == null ? Instant.now(clock) : block.getCreatedAt());
    }
}
