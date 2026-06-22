package io.github.orange2652.partner.channel.saga.ordr.application;

import io.github.orange2652.partner.channel.saga.ordr.domain.SagaState;
import io.github.orange2652.partner.channel.saga.ordr.domain.SagaStateNotFoundException;
import io.github.orange2652.partner.channel.saga.ordr.domain.SagaStateRepository;
import io.github.orange2652.partner.channel.shared.domain.DeliveryPenaltyCharger;
import io.github.orange2652.partner.channel.shared.event.ordr.CompensateChannelConfirmRequested;
import io.github.orange2652.partner.channel.shared.idempotency.EventKey;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Component;

/**
 * A1 보상 시작 (R4) — 두 트리거({@link ValidateReplyHandler} 의 validate REJECTED /
 * {@link ChannelConfirmReplyHandler} 의 channelConfirm FAILED)가 공유하는 보상 진입부.
 *
 * <p>한 saga 당 트리거는 하나만 발생한다(validate 거절 시 channelConfirm 자체가 실행되지 않음). 보상은 역순 LIFO
 * (외부 cancel → staging 취소)이므로 시작은 외부 cancel 요청({@link CompensateChannelConfirmRequested})부터 낸다.
 * validate 거절도 외부 주문이 PAID 사실이라 동일하게 seller-cancel 한다(전용 액션 없이 step3 보상 메커니즘 재사용 —
 * R4 D6, from-status 차이는 adapter 사전 GET 가드가 흡수). 보상은 셀러 주도라 배송비 귀책은 항상
 * {@link DeliveryPenaltyCharger#MERCHANT}(R6 D5) — event 에 실어 adapter 로 운반한다. 추가 맥락은 {@code reason} 에.</p>
 *
 * <p><b>멱등(R3)</b>: 트리거 reply 가 두 번 와도 한 번만 보상을 시작하도록 {@code markIfFirst("compensateStart", ...)}
 * 로 가드한다. 마킹·{@code COMPENSATING} 전이는 같은 saga_schema 로컬 Tx.</p>
 */
@Slf4j
@Component
@RequiredArgsConstructor
class OrderSagaCompensator {

    private static final String CONSUMER = "compensateStart";

    private final SagaIdempotencyGuard idempotencyGuard;
    private final SagaStateRepository sagaStateRepository;
    private final ApplicationEventPublisher events;

    void start(UUID sagaId, String reason) {
        SagaState state = sagaStateRepository.findBySagaId(sagaId)
                .orElseThrow(() -> new SagaStateNotFoundException(sagaId));

        if (!idempotencyGuard.markIfFirst(CONSUMER, state.correlationKey())) {
            log.info("보상 시작 skip (이미 진행 중): sagaId={} correlationKey={}", sagaId, state.correlationKey());
            return;
        }

        sagaStateRepository.save(state.withStatus(A1OrderSaga.STATUS_COMPENSATING));
        events.publishEvent(new CompensateChannelConfirmRequested(
                sagaId,
                EventKey.channelCodeOf(state.correlationKey()),
                EventKey.externalOrderProductIdOf(state.correlationKey()),
                reason,
                DeliveryPenaltyCharger.MERCHANT));
        log.info("보상 시작 → COMPENSATING + 외부 cancel 요청: sagaId={} correlationKey={} reason={}",
                sagaId, state.correlationKey(), reason);
    }
}
