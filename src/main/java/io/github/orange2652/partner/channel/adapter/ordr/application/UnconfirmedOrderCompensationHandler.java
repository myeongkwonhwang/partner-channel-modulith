package io.github.orange2652.partner.channel.adapter.ordr.application;

import io.github.orange2652.partner.channel.adapter.ordr.domain.StagingOrder;
import io.github.orange2652.partner.channel.adapter.ordr.domain.StagingOrderRepository;
import io.github.orange2652.partner.channel.shared.event.ordr.CompensateUnconfirmedOrderRequested;
import io.github.orange2652.partner.channel.shared.event.ordr.UnconfirmedOrderCompensated;
import io.github.orange2652.partner.channel.shared.idempotency.EventKey;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.modulith.events.ApplicationModuleListener;
import org.springframework.stereotype.Component;

/**
 * A1 보상 — unconfirmedOrder(step1) staging 취소 (R4). saga 의 {@link CompensateUnconfirmedOrderRequested} 를 받아
 * staging row 를 CANCELED 로 전이한다(DELETE 아님). 외부 호출이 없는 <b>순수 DB</b> 보상이라 step1 과 같은 컨벤션:
 * {@link ApplicationModuleListener} 단일 Tx + mark-before(같은 channel_schema 로컬 Tx).
 *
 * <p><b>멱등(R3)</b>: {@code (compensate:unconfirmedOrder, eventId)} 마킹과 staging UPDATE 가 같은 Tx 라 원자적이다.
 * 이미 CANCELED 면 {@link StagingOrder#canceled()} 가 NoOp(자기 반환)이고, row 가 없어도(이론상 없음) 취소 목표는
 * 충족된 것으로 보고 OK 를 발행한다. 실패(DB 오류)는 예외 전파 → Tx 롤백(마킹 포함) → 재전달로 재시도(step1 동일).</p>
 */
@Slf4j
@Component
@RequiredArgsConstructor
class UnconfirmedOrderCompensationHandler {

    private static final String CONSUMER = "compensate:unconfirmedOrder";

    private final AdapterIdempotencyGuard idempotencyGuard;
    private final StagingOrderRepository stagingOrderRepository;
    private final ApplicationEventPublisher events;

    @ApplicationModuleListener
    void onCompensateUnconfirmedOrder(CompensateUnconfirmedOrderRequested command) {
        String eventId = EventKey.of(command.channel(), command.externalOrderProductId());

        if (!idempotencyGuard.markIfFirst(CONSUMER, eventId)) {
            log.info("compensate:unconfirmedOrder skip (이미 처리됨): sagaId={} eventId={}",
                    command.sagaId(), eventId);
            return;
        }

        Optional<StagingOrder> staging = stagingOrderRepository.findByChannelAndExternalOrderProductId(
                command.channel(), command.externalOrderProductId());
        staging.map(StagingOrder::canceled).ifPresent(stagingOrderRepository::save);

        events.publishEvent(UnconfirmedOrderCompensated.ok(command.sagaId()));
        log.info("staging 취소(CANCELED 전이): sagaId={} eventId={} found={}",
                command.sagaId(), eventId, staging.isPresent());
    }
}
