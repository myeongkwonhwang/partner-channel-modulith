package io.github.orange2652.partner.channel.saga.ordr.application;

import io.github.orange2652.partner.channel.saga.ordr.domain.SagaState;
import io.github.orange2652.partner.channel.saga.ordr.domain.SagaStateNotFoundException;
import io.github.orange2652.partner.channel.saga.ordr.domain.SagaStateRepository;
import io.github.orange2652.partner.channel.shared.event.ordr.UnconfirmedOrderCompensated;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.modulith.events.ApplicationModuleListener;
import org.springframework.stereotype.Component;

/**
 * A1 보상 — staging 취소(step1 보상) 결과 수신 (R4). adapter 의 {@link UnconfirmedOrderCompensated} 를 받아 보상
 * 체인의 마지막 전이를 한다(LIFO 마지막 — 외부 cancel → staging 취소 모두 끝).
 *
 * <ul>
 *   <li>OK → {@code COMPENSATED} terminal(보상 완료, race-safe 멱등 가드).</li>
 *   <li>FAILED → COMPENSATING 유지(transient — R5a scanner/재전달로 재시도). 미소비 상태로 둔다(로그만).</li>
 * </ul>
 */
@Slf4j
@Component
@RequiredArgsConstructor
class UnconfirmedOrderCompensatedHandler {

    private static final String CONSUMER = "unconfirmedOrderCompensated";

    private final SagaIdempotencyGuard idempotencyGuard;
    private final SagaStateRepository sagaStateRepository;

    @ApplicationModuleListener
    void onUnconfirmedOrderCompensated(UnconfirmedOrderCompensated reply) {
        if (reply.result() == UnconfirmedOrderCompensated.Result.FAILED) {
            log.info("staging 취소 FAILED → COMPENSATING 유지(재시도 대기): sagaId={}", reply.sagaId());
            return;
        }

        SagaState state = sagaStateRepository.findBySagaId(reply.sagaId())
                .orElseThrow(() -> new SagaStateNotFoundException(reply.sagaId()));

        if (!idempotencyGuard.markIfFirst(CONSUMER, state.correlationKey())) {
            log.info("unconfirmedOrderCompensated skip (이미 처리됨): sagaId={} correlationKey={}",
                    reply.sagaId(), state.correlationKey());
            return;
        }

        sagaStateRepository.save(state.withStatus(A1OrderSaga.STATUS_COMPENSATED));
        log.info("staging 취소 완료 → COMPENSATED (보상 종료): sagaId={} correlationKey={}",
                reply.sagaId(), state.correlationKey());
    }
}
