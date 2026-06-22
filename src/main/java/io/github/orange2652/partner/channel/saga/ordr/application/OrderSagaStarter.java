package io.github.orange2652.partner.channel.saga.ordr.application;

import io.github.orange2652.partner.channel.saga.ordr.domain.SagaState;
import io.github.orange2652.partner.channel.saga.ordr.domain.SagaStateRepository;
import io.github.orange2652.partner.channel.shared.event.ordr.OrderReceivedEvent;
import io.github.orange2652.partner.channel.shared.event.ordr.UnconfirmedOrderCommand;
import io.github.orange2652.partner.channel.shared.idempotency.EventKey;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.modulith.events.ApplicationModuleListener;
import org.springframework.stereotype.Component;

/**
 * A1 sagaStart (R2) — 자사 polling 이 발행한 {@link OrderReceivedEvent} 를 in-VM 으로 받아 A1 saga 를 시작한다.
 *
 * <p>외부 Kafka {@code channel.order.received} 는 외부 알림용 병행 발행이고, saga 는 그 토픽을 되읽지 않는다
 * (self-consume 회피 — R2). 본 listener 가 in-VM Application Event 로 트리거된다.</p>
 *
 * <p><b>멱등(R3)</b>: at-least-once 트리거에서 saga 인스턴스 중복 생성을 막기 위해 {@code (sagaStart, correlationKey)}
 * 를 {@link SagaIdempotencyGuard} 로 먼저 가드한다. 신규일 때만 saga_state 를 만들고 step1 command 를 발행한다.</p>
 *
 * <p><b>Tx</b>: {@link ApplicationModuleListener} 는 비동기 + 자체 트랜잭션이다. 멱등 마킹과 saga_state INSERT 는
 * 같은 saga_schema 로컬 Tx 라 원자적이고, 발행한 {@link UnconfirmedOrderCommand} 는 내장 outbox
 * ({@code event_publication})를 통해 커밋 후 dispatch 된다. 외부 호출은 본 흐름에 없다.</p>
 */
@Slf4j
@Component
@RequiredArgsConstructor
class OrderSagaStarter {

    private static final String CONSUMER = "sagaStart";

    private final SagaIdempotencyGuard idempotencyGuard;
    private final SagaStateRepository sagaStateRepository;
    private final ApplicationEventPublisher events;

    @ApplicationModuleListener
    void onOrderReceived(OrderReceivedEvent event) {
        String correlationKey = EventKey.of(event.channel(), event.externalOrderProductId());

        if (!idempotencyGuard.markIfFirst(CONSUMER, correlationKey)) {
            log.info("sagaStart skip (이미 시작됨): correlationKey={}", correlationKey);
            return;
        }

        UUID sagaId = UUID.randomUUID();
        sagaStateRepository.save(SagaState.start(
                sagaId, A1OrderSaga.TYPE, correlationKey, A1OrderSaga.STEP_UNCONFIRMED_ORDER, event.rawPayload()));
        events.publishEvent(new UnconfirmedOrderCommand(
                sagaId, event.channel(), event.externalOrderProductId(), event.rawPayload()));

        log.info("sagaStart: sagaId={} correlationKey={}", sagaId, correlationKey);
    }
}
