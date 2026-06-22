package io.github.orange2652.partner.channel.saga.ordr.application;

import io.github.orange2652.partner.channel.saga.ordr.domain.SagaState;
import io.github.orange2652.partner.channel.saga.ordr.domain.SagaStateNotFoundException;
import io.github.orange2652.partner.channel.saga.ordr.domain.SagaStateRepository;
import io.github.orange2652.partner.channel.shared.event.ordr.ChannelConfirmReply;
import io.github.orange2652.partner.channel.shared.event.ordr.ConfirmedOrderCommand;
import io.github.orange2652.partner.channel.shared.idempotency.EventKey;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.modulith.events.ApplicationModuleListener;
import org.springframework.stereotype.Component;

/**
 * A1 step3 reply 수신 (R2) — adapter 의 {@link ChannelConfirmReply} 를 받아 saga_state 를 다음 step(CONFIRMED_ORDER)
 * 으로 advance 하고 step4 트리거 {@link ConfirmedOrderCommand} 를 발행한다(오케스트레이션: reply 마다 advance + 다음
 * command).
 *
 * <p>OK → CONFIRMED_ORDER 로 advance(race-safe 멱등 가드) + step4 발행. {@code ConfirmedOrderCommand} 의 channel 은
 * {@code correlationKey}({@link EventKey#channelCodeOf}), raw 는 {@code payload}(sagaStart 가 저장한 주문 원본)에서
 * 소싱한다 — step3 외부 통보는 상태 전이라 별도 응답 본문이 없으므로 core 의 Pivot(주문 확정·물류 전송)에는 원
 * 주문 원본을 그대로 넘긴다.</p>
 *
 * <p>FAILED(외부 통보 실패)는 R4 보상(step3 외부 cancel + staging 취소) 트리거다. {@link OrderSagaCompensator#start}
 * 로 위임하며, 멱등/COMPENSATING 전이는 compensator 가 가드한다.</p>
 */
@Slf4j
@Component
@RequiredArgsConstructor
class ChannelConfirmReplyHandler {

    private static final String CONSUMER = "channelConfirmReply";

    private final SagaIdempotencyGuard idempotencyGuard;
    private final SagaStateRepository sagaStateRepository;
    private final ApplicationEventPublisher events;
    private final OrderSagaCompensator compensator;

    @ApplicationModuleListener
    void onChannelConfirmReply(ChannelConfirmReply reply) {
        if (reply.result() == ChannelConfirmReply.Result.FAILED) {
            log.info("channelConfirm FAILED → 보상 시작(외부 cancel + staging): sagaId={} errorCode={}",
                    reply.sagaId(), reply.errorCode());
            compensator.start(reply.sagaId(), "CHANNEL_CONFIRM_FAILED:" + reply.errorCode());
            return;
        }

        SagaState state = sagaStateRepository.findBySagaId(reply.sagaId())
                .orElseThrow(() -> new SagaStateNotFoundException(reply.sagaId()));

        if (!idempotencyGuard.markIfFirst(CONSUMER, state.correlationKey())) {
            log.info("channelConfirmReply skip (이미 처리됨): sagaId={} correlationKey={}",
                    reply.sagaId(), state.correlationKey());
            return;
        }

        sagaStateRepository.save(state.advanceTo(A1OrderSaga.STEP_CONFIRMED_ORDER));
        events.publishEvent(new ConfirmedOrderCommand(
                reply.sagaId(), EventKey.channelCodeOf(state.correlationKey()), state.payload()));
        log.info("step3 통과 → CONFIRMED_ORDER: sagaId={} correlationKey={}",
                reply.sagaId(), state.correlationKey());
    }
}
