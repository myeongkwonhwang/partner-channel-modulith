package io.github.orange2652.partner.channel.saga.ordr.application;

import io.github.orange2652.partner.channel.saga.ordr.domain.SagaState;
import io.github.orange2652.partner.channel.saga.ordr.domain.SagaStateNotFoundException;
import io.github.orange2652.partner.channel.saga.ordr.domain.SagaStateRepository;
import io.github.orange2652.partner.channel.shared.event.ordr.ChannelConfirmCompensated;
import io.github.orange2652.partner.channel.shared.event.ordr.CompensateUnconfirmedOrderRequested;
import io.github.orange2652.partner.channel.shared.idempotency.EventKey;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.modulith.events.ApplicationModuleListener;
import org.springframework.stereotype.Component;

/**
 * A1 보상 — 외부 cancel(step3 보상) 결과 수신 (R4). adapter 의 {@link ChannelConfirmCompensated} 를 받아 LIFO 다음
 * 보상(staging 취소)으로 진행하거나 terminal 로 전이한다(오케스트레이션).
 *
 * <ul>
 *   <li>OK → staging 취소 요청 {@link CompensateUnconfirmedOrderRequested} 발행(race-safe 멱등 가드).</li>
 *   <li>MANUAL_REQUIRED → {@code PENDING_MANUAL_CANCEL} terminal(외부 cancel 미가용/영구실패 — 운영 개입). 상태 전이는
 *       멱등(재수신해도 같은 terminal).</li>
 *   <li>FAILED → COMPENSATING 유지(transient — R5a scanner/재전달로 재시도). 미소비 상태로 둔다(로그만).</li>
 * </ul>
 */
@Slf4j
@Component
@RequiredArgsConstructor
class ChannelConfirmCompensatedHandler {

    private static final String CONSUMER = "channelConfirmCompensated";

    private final SagaIdempotencyGuard idempotencyGuard;
    private final SagaStateRepository sagaStateRepository;
    private final ApplicationEventPublisher events;

    @ApplicationModuleListener
    void onChannelConfirmCompensated(ChannelConfirmCompensated reply) {
        SagaState state = sagaStateRepository.findBySagaId(reply.sagaId())
                .orElseThrow(() -> new SagaStateNotFoundException(reply.sagaId()));

        switch (reply.result()) {
            case MANUAL_REQUIRED -> {
                sagaStateRepository.save(state.withStatus(A1OrderSaga.STATUS_PENDING_MANUAL_CANCEL));
                log.info("외부 cancel MANUAL_REQUIRED → PENDING_MANUAL_CANCEL: sagaId={} correlationKey={}",
                        reply.sagaId(), state.correlationKey());
            }
            case FAILED -> log.info("외부 cancel FAILED → COMPENSATING 유지(재시도 대기): sagaId={} correlationKey={}",
                    reply.sagaId(), state.correlationKey());
            case OK -> {
                if (!idempotencyGuard.markIfFirst(CONSUMER, state.correlationKey())) {
                    log.info("channelConfirmCompensated skip (이미 처리됨): sagaId={} correlationKey={}",
                            reply.sagaId(), state.correlationKey());
                    return;
                }
                events.publishEvent(new CompensateUnconfirmedOrderRequested(
                        reply.sagaId(),
                        EventKey.channelCodeOf(state.correlationKey()),
                        EventKey.externalOrderProductIdOf(state.correlationKey()),
                        "COMPENSATE_AFTER_CHANNEL_CANCEL"));
                log.info("외부 cancel 완료 → staging 취소 요청: sagaId={} correlationKey={}",
                        reply.sagaId(), state.correlationKey());
            }
        }
    }
}
