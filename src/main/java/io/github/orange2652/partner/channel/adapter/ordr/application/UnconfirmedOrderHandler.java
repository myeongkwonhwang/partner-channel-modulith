package io.github.orange2652.partner.channel.adapter.ordr.application;

import io.github.orange2652.partner.channel.adapter.ordr.domain.StagingOrder;
import io.github.orange2652.partner.channel.adapter.ordr.domain.StagingOrderRepository;
import io.github.orange2652.partner.channel.shared.event.ordr.UnconfirmedOrderCommand;
import io.github.orange2652.partner.channel.shared.event.ordr.UnconfirmedOrderReply;
import io.github.orange2652.partner.channel.shared.idempotency.EventKey;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.modulith.events.ApplicationModuleListener;
import org.springframework.stereotype.Component;

/**
 * A1 step1 unconfirmedOrder (R2) — saga 가 발행한 {@link UnconfirmedOrderCommand} 를 받아 외부 raw 를 staging 에
 * 미확정 적재한다(외부 PREPARING_PRODUCT 전이는 step3 channelConfirm 으로 분리 — R2 분기).
 *
 * <p><b>멱등(R3)</b>: {@code (unconfirmedOrder, "{channel}:{externalOrderProductId}")} 를 {@link AdapterIdempotencyGuard}
 * 로 먼저 가드한다. 신규일 때만 staging INSERT + reply 발행. 중복이면 skip(원본이 이미 reply 했다).</p>
 *
 * <p><b>Tx</b>: {@link ApplicationModuleListener} 는 비동기 + 자체 트랜잭션. 멱등 마킹과 staging INSERT 는 같은
 * channel_schema 로컬 Tx 라 원자적이고, {@link UnconfirmedOrderReply} 는 내장 outbox 로 커밋 후 dispatch 된다.
 * step1 에는 외부 호출이 없다(외부 통보는 step3).</p>
 *
 * <p>실패-reply(예외 시 {@code failed} 발행)·보상은 이후 increment. 현재는 정상 경로 + 멱등 skip 만 — 예외는
 * 그대로 전파돼 Tx 롤백(멱등 마킹 포함)되므로 재시도 가능하다.</p>
 */
@Slf4j
@Component
@RequiredArgsConstructor
class UnconfirmedOrderHandler {

    private static final String CONSUMER = "unconfirmedOrder";

    private final AdapterIdempotencyGuard idempotencyGuard;
    private final StagingOrderRepository stagingOrderRepository;
    private final ApplicationEventPublisher events;

    @ApplicationModuleListener
    void onUnconfirmedOrder(UnconfirmedOrderCommand command) {
        String eventId = EventKey.of(command.channel(), command.externalOrderProductId());

        if (!idempotencyGuard.markIfFirst(CONSUMER, eventId)) {
            log.info("unconfirmedOrder skip (이미 처리됨): sagaId={} eventId={}", command.sagaId(), eventId);
            return;
        }

        StagingOrder saved = stagingOrderRepository.save(
                StagingOrder.newRecord(command.channel(), command.externalOrderProductId(), command.raw()));
        events.publishEvent(UnconfirmedOrderReply.ok(command.sagaId(), saved.id()));

        log.info("unconfirmedOrder: sagaId={} stagingId={} eventId={}", command.sagaId(), saved.id(), eventId);
    }
}
