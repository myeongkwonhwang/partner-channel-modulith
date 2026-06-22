package io.github.orange2652.partner.channel.saga.ordr.application;

import io.github.orange2652.partner.channel.saga.ordr.domain.SagaState;
import io.github.orange2652.partner.channel.saga.ordr.domain.SagaStateNotFoundException;
import io.github.orange2652.partner.channel.saga.ordr.domain.SagaStateRepository;
import io.github.orange2652.partner.channel.shared.event.ordr.ChannelConfirmCommand;
import io.github.orange2652.partner.channel.shared.event.ordr.ValidateReply;
import io.github.orange2652.partner.channel.shared.idempotency.EventKey;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.modulith.events.ApplicationModuleListener;
import org.springframework.stereotype.Component;

/**
 * A1 step2 reply 수신 (R2) — core 의 {@link ValidateReply} 를 받아 saga_state 를 advance 한다(오케스트레이션).
 *
 * <p>PASSED → CHANNEL_CONFIRM 로 advance(race-safe 멱등 가드) + step3 트리거 {@link ChannelConfirmCommand} 발행
 * (오케스트레이션: reply 마다 advance + 다음 command — dangling 회피). command 의 channel/externalOrderProductId 는
 * saga 컨텍스트의 {@code correlationKey}({@code "{channel}:{externalOrderProductId}"})에서 {@link EventKey} 로 소싱한다
 * (step1 의 channel/raw 소싱과 동일 패턴).</p>
 *
 * <p>REJECTED(판매 불가)는 R4 보상 트리거다 — 외부 주문이 PAID 사실이라 무페널티 출구가 없어 seller-cancel +
 * staging 취소로 보상한다(배송 페널티 귀책 셀러). {@link OrderSagaCompensator#start} 로 위임하며, 멱등/COMPENSATING
 * 전이는 compensator 가 가드한다.</p>
 */
@Slf4j
@Component
@RequiredArgsConstructor
class ValidateReplyHandler {

    private static final String CONSUMER = "validateReply";

    private final SagaIdempotencyGuard idempotencyGuard;
    private final SagaStateRepository sagaStateRepository;
    private final ApplicationEventPublisher events;
    private final OrderSagaCompensator compensator;

    @ApplicationModuleListener
    void onValidateReply(ValidateReply reply) {
        if (reply.result() == ValidateReply.Result.REJECTED) {
            log.info("validate REJECTED → 보상 시작(seller-cancel + staging, 귀책 MERCHANT): sagaId={} reason={}",
                    reply.sagaId(), reply.reasonCode());
            compensator.start(reply.sagaId(),
                    "VALIDATE_REJECTED:" + reply.reasonCode() + ":deliveryPenaltyCharger=MERCHANT");
            return;
        }

        SagaState state = sagaStateRepository.findBySagaId(reply.sagaId())
                .orElseThrow(() -> new SagaStateNotFoundException(reply.sagaId()));

        if (!idempotencyGuard.markIfFirst(CONSUMER, state.correlationKey())) {
            log.info("validateReply skip (이미 처리됨): sagaId={} correlationKey={}",
                    reply.sagaId(), state.correlationKey());
            return;
        }

        sagaStateRepository.save(state.advanceTo(A1OrderSaga.STEP_CHANNEL_CONFIRM));
        events.publishEvent(new ChannelConfirmCommand(
                reply.sagaId(),
                EventKey.channelCodeOf(state.correlationKey()),
                EventKey.externalOrderProductIdOf(state.correlationKey())));
        log.info("step2 통과 → CHANNEL_CONFIRM: sagaId={} correlationKey={}",
                reply.sagaId(), state.correlationKey());
    }
}
