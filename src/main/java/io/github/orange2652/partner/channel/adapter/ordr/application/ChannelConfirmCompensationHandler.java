package io.github.orange2652.partner.channel.adapter.ordr.application;

import io.github.orange2652.partner.channel.adapter.ordr.domain.ManualCancelRequiredException;
import io.github.orange2652.partner.channel.adapter.ordr.domain.UnsupportedChannelException;
import io.github.orange2652.partner.channel.shared.domain.CancelReason;
import io.github.orange2652.partner.channel.shared.domain.Channel;
import io.github.orange2652.partner.channel.shared.event.ordr.ChannelConfirmCompensated;
import io.github.orange2652.partner.channel.shared.event.ordr.CompensateChannelConfirmRequested;
import io.github.orange2652.partner.channel.shared.idempotency.EventKey;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * A1 보상 — channelConfirm(step3) 외부 cancel (R4). saga 의 {@link CompensateChannelConfirmRequested} 를 받아 외부
 * 채널 주문을 seller-cancel 한다. step3 confirm 과 같은 외부 호출 step 이라 동일한 Tx 분리 컨벤션을 따른다
 * (얇은 비-Tx 리스너 + {@link AdapterTxSteps}, 외부 호출은 Tx 밖).
 *
 * <p><b>멱등 마킹 시점이 step3 confirm 과 의도적으로 다르다</b>: confirm 은 외부 전이가 비멱등이라 mark-<b>before</b>
 * 였지만, cancel 은 {@code CompensationPort} 가 사전 GET 가드로 멱등(이미 취소면 NoOp)이라 중복 호출이 무해하다.
 * 그래서 cancel 성공 <b>후</b> 마킹한다 — 이렇게 해야 transient {@code FAILED} 시 R5a scanner 재발행이 마킹에 막히지
 * 않고 재시도된다(R4: FAILED → COMPENSATING 유지 재시도).</p>
 *
 * <ul>
 *   <li>cancel 성공 → mark 후 {@link ChannelConfirmCompensated#ok}.</li>
 *   <li>Port 미등록(외부 cancel 미가용) 또는 {@link ManualCancelRequiredException}(영구 불가) →
 *       {@link ChannelConfirmCompensated#manualRequired}(saga → PENDING_MANUAL_CANCEL).</li>
 *   <li>그 외 RuntimeException(transient) → {@link ChannelConfirmCompensated#failed}(saga COMPENSATING 유지 재시도).</li>
 * </ul>
 */
@Slf4j
@Component
@RequiredArgsConstructor
class ChannelConfirmCompensationHandler {

    private static final String CONSUMER = "compensate:channelConfirm";

    private final CompensationPortRegistry registry;
    private final AdapterTxSteps txSteps;

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    void onCompensateChannelConfirm(CompensateChannelConfirmRequested command) {
        Channel channel = Channel.fromCode(command.channel())
                .orElseThrow(() -> new UnsupportedChannelException(command.channel(), command.sagaId()));
        String eventId = EventKey.of(channel, command.externalOrderProductId());

        Optional<CompensationPort> port = registry.find(channel);
        if (port.isEmpty()) {
            log.info("외부 cancel 미가용(Port 미등록) → MANUAL_REQUIRED: sagaId={} eventId={}",
                    command.sagaId(), eventId);
            txSteps.publishReply(ChannelConfirmCompensated.manualRequired(command.sagaId()));
            return;
        }

        CancelReason cancelReason = CancelReason.of(command.deliveryPenaltyCharger(), command.reason());
        try {
            port.get().cancel(command.externalOrderProductId(), cancelReason);
        } catch (ManualCancelRequiredException e) {
            log.info("외부 cancel 영구 불가 → MANUAL_REQUIRED: sagaId={} eventId={} reason={}",
                    command.sagaId(), eventId, e.getMessage());
            txSteps.publishReply(ChannelConfirmCompensated.manualRequired(command.sagaId()));
            return;
        } catch (RuntimeException e) {
            log.info("외부 cancel 실패(transient) → FAILED(재시도 대기): sagaId={} eventId={} error={}",
                    command.sagaId(), eventId, e.getMessage());
            txSteps.publishReply(ChannelConfirmCompensated.failed(command.sagaId()));
            return;
        }

        if (!txSteps.mark(CONSUMER, eventId)) {
            log.info("compensate:channelConfirm skip (이미 보상됨): sagaId={} eventId={}", command.sagaId(), eventId);
            return;
        }
        txSteps.publishReply(ChannelConfirmCompensated.ok(command.sagaId()));
        log.info("외부 cancel 완료: sagaId={} eventId={}", command.sagaId(), eventId);
    }
}
