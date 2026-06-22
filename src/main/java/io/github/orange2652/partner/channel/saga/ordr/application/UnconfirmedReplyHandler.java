package io.github.orange2652.partner.channel.saga.ordr.application;

import io.github.orange2652.partner.channel.saga.ordr.domain.SagaState;
import io.github.orange2652.partner.channel.saga.ordr.domain.SagaStateNotFoundException;
import io.github.orange2652.partner.channel.saga.ordr.domain.SagaStateRepository;
import io.github.orange2652.partner.channel.shared.event.ordr.UnconfirmedOrderReply;
import io.github.orange2652.partner.channel.shared.event.ordr.ValidateCommand;
import io.github.orange2652.partner.channel.shared.idempotency.EventKey;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.modulith.events.ApplicationModuleListener;
import org.springframework.stereotype.Component;

/**
 * A1 step1 reply 수신 (R2) — adapter 의 {@link UnconfirmedOrderReply} 를 받아 saga_state 를 다음 step(VALIDATE)으로
 * advance 한다(오케스트레이션).
 *
 * <p><b>멱등/race(R3)</b>: 같은 reply 가 두 번 와도 한 번만 advance 해야 한다. {@link SagaIdempotencyGuard} 의 원자
 * {@code markIfFirst("unconfirmedOrderReply", correlationKey)} 로 가드한다 — read-modify-write(load→advance→save)의
 * lost-update 를 막는 race-safe 게이트. 마킹과 advance(save)는 같은 saga_schema 로컬 Tx.</p>
 *
 * <p>OK → VALIDATE 로 advance + step2 트리거 {@link ValidateCommand} 발행(오케스트레이션: reply 마다 advance + 다음
 * command). {@code ValidateCommand} 의 channel/raw 는 saga 컨텍스트에서 소싱한다 — channel 은 {@code correlationKey}
 * ({@link EventKey#channelCodeOf}), raw 는 {@code payload}(sagaStart 가 저장한 rawPayload). FAILED(비즈니스 실패)는
 * R4 보상 increment 대상으로, 현재는 식별 로그만 남긴다.</p>
 */
@Slf4j
@Component
@RequiredArgsConstructor
class UnconfirmedReplyHandler {

    private static final String CONSUMER = "unconfirmedOrderReply";

    private final SagaIdempotencyGuard idempotencyGuard;
    private final SagaStateRepository sagaStateRepository;
    private final ApplicationEventPublisher events;

    @ApplicationModuleListener
    void onUnconfirmedOrderReply(UnconfirmedOrderReply reply) {
        if (reply.result() == UnconfirmedOrderReply.Result.FAILED) {
            log.info("unconfirmedOrder FAILED (보상은 이후 increment): sagaId={} errorCode={}",
                    reply.sagaId(), reply.errorCode());
            return;
        }

        SagaState state = sagaStateRepository.findBySagaId(reply.sagaId())
                .orElseThrow(() -> new SagaStateNotFoundException(reply.sagaId()));

        if (!idempotencyGuard.markIfFirst(CONSUMER, state.correlationKey())) {
            log.info("unconfirmedOrderReply skip (이미 처리됨): sagaId={} correlationKey={}",
                    reply.sagaId(), state.correlationKey());
            return;
        }

        sagaStateRepository.save(state.advanceTo(A1OrderSaga.STEP_VALIDATE));
        events.publishEvent(new ValidateCommand(
                reply.sagaId(), EventKey.channelCodeOf(state.correlationKey()), state.payload()));
        log.info("step1 완료 → VALIDATE: sagaId={} correlationKey={} stagingId={}",
                reply.sagaId(), state.correlationKey(), reply.stagingId());
    }
}
