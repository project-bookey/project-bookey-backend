package app.bookey.batch;

import app.bookey.api.push.PushCampaignRunner;
import app.bookey.domain.admin.OpsFlag;
import app.bookey.domain.admin.OpsFlagRepository;
import app.bookey.domain.push.PushCampaign;
import app.bookey.domain.push.PushCampaignRepository;
import app.bookey.domain.push.PushCampaignStatus;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * 관리자 전체 푸시 발송. 1분마다 예약된 캠페인을 시작하고, 대상자를 펼치고, 때가 된 알림을 묶어 보낸다.
 * 푸시 킬스위치(PUSH_ENABLED)가 꺼져 있으면 아무것도 하지 않는다 — 캠페인은 멈춰 있다가 켜면 이어서 나간다.
 * 한 번에 펼치고 보내는 양을 제한해(분당 최대 5,000명·2,500건) Expo 와 DB 에 몰리지 않게 한다.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class PushCampaignJob {

    private static final int MAX_CHUNKS_PER_RUN = 10;
    private static final int MAX_BATCHES_PER_RUN = 5;

    private final PushCampaignRunner runner;
    private final PushCampaignRepository campaignRepository;
    private final OpsFlagRepository opsFlagRepository;

    @Scheduled(fixedDelay = 60 * 1000, initialDelay = 45 * 1000)
    public void run() {
        boolean pushEnabled = opsFlagRepository.findById(OpsFlag.PUSH_ENABLED).map(OpsFlag::isEnabled).orElse(true);
        if (!pushEnabled) {
            log.debug("PushCampaignJob: push kill switch is off — campaigns paused");
            return;
        }
        runner.startDue().forEach(id -> log.info("PushCampaignJob: campaign {} started", id));

        for (PushCampaign campaign : campaignRepository.findAllByStatusOrderByIdAsc(PushCampaignStatus.SENDING)) {
            try {
                for (int i = 0; i < MAX_CHUNKS_PER_RUN && runner.expandNext(campaign.getId()); i++) {
                    // 다음 묶음
                }
            } catch (ObjectOptimisticLockingFailureException e) {
                log.info("PushCampaignJob: campaign {} is being expanded elsewhere", campaign.getId());
            } catch (RuntimeException e) {
                log.warn("PushCampaignJob: failed to expand campaign {}", campaign.getId(), e);
            }
        }

        int sent = 0;
        for (int i = 0; i < MAX_BATCHES_PER_RUN; i++) {
            int batch = runner.deliverDue();
            sent += batch;
            if (batch == 0) {
                break;
            }
        }
        if (sent > 0) {
            log.info("PushCampaignJob: {} campaign notifications sent", sent);
        }

        for (PushCampaign campaign : campaignRepository.findAllByStatusOrderByIdAsc(PushCampaignStatus.SENDING)) {
            runner.finishIfDone(campaign.getId());
        }
    }
}
