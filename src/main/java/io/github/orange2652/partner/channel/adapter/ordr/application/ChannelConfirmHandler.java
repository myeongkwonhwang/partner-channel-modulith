package io.github.orange2652.partner.channel.adapter.ordr.application;

import io.github.orange2652.partner.channel.adapter.ordr.domain.UnsupportedChannelException;
import io.github.orange2652.partner.channel.shared.domain.Channel;
import io.github.orange2652.partner.channel.shared.event.ordr.ChannelConfirmCommand;
import io.github.orange2652.partner.channel.shared.event.ordr.ChannelConfirmReply;
import io.github.orange2652.partner.channel.shared.idempotency.EventKey;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * A1 step3 channelConfirm (R2) — saga 의 {@link ChannelConfirmCommand} 를 받아 외부 채널에 주문 수락을 통보한다
 * (토스 PAID→PREPARING_PRODUCT 등). saga 전체에서 <b>첫 외부 호출</b> step.
 *
 * <p><b>외부 호출 Tx 분리(전역 컨벤션, java-spring-expert 자문)</b>: 본 리스너는 {@code @ApplicationModuleListener}
 * (자체 {@code REQUIRES_NEW} Tx)를 <b>쓰지 않는다</b> — 그러면 외부 호출이 리스너 Tx 안에 갇힌다. 대신
 * {@code @TransactionalEventListener(AFTER_COMMIT)} 로 두어 본문에 ambient Tx 가 없게 하고, DB 작업
 * (멱등 마킹·reply 발행)만 {@link AdapterTxSteps} 의 {@code REQUIRES_NEW} 협력 메서드로 분리한다.
 * {@link ConfirmStrategy#confirm}(외부 WRITE)은 두 협력 호출 <b>사이</b>에서 Tx 없이 실행된다.</p>
 *
 * <p><b>순서/멱등(R3)</b>: 채널·전략을 먼저 resolve(설정 오류는 멱등 슬롯 소비/보상 트리거 없이 fail-fast) →
 * {@code mark} 커밋(외부 호출 전, 중복 외부 WRITE 차단) → {@code confirm} → reply.ok 발행. {@code confirm} 실패는
 * reply.failed 로 발행해 saga 를 step3 보상(R4)으로 보낸다(보상 소비는 별도 increment).</p>
 *
 * <p><b>크래시 복구</b>: mark 커밋 후 reply 전 크래시(외부는 성공)는 마킹만으로 판단하면 split-brain 이다 —
 * R5a {@code SagaTimeoutScanner} 가 stuck CHANNEL_CONFIRM 을 외부 GET(사전 가드)으로 advance/보상 판정한다
 * (마킹은 중복 confirm 방지용일 뿐 advance 근거가 아님). 비동기(@Async) 전환·outbox 통합 검증은 Phase 2
 * {@code @ApplicationModuleTest} 증분으로 이연(@EnableAsync 미구성).</p>
 */
@Slf4j
@Component
@RequiredArgsConstructor
class ChannelConfirmHandler {

    private static final String CONSUMER = "channelConfirm";

    private final ConfirmStrategyRegistry registry;
    private final AdapterTxSteps txSteps;

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    void onChannelConfirm(ChannelConfirmCommand command) {
        Channel channel = Channel.fromCode(command.channel())
                .orElseThrow(() -> new UnsupportedChannelException(command.channel(), command.sagaId()));
        ConfirmStrategy strategy = registry.resolve(channel);

        String eventId = EventKey.of(channel, command.externalOrderProductId());
        if (!txSteps.mark(CONSUMER, eventId)) {
            log.info("channelConfirm skip (이미 처리됨): sagaId={} eventId={}", command.sagaId(), eventId);
            return;
        }

        try {
            strategy.confirm(command.externalOrderProductId());
        } catch (RuntimeException e) {
            log.info("channelConfirm 외부 통보 실패 → 보상 대상(R4): sagaId={} eventId={} error={}",
                    command.sagaId(), eventId, e.getMessage());
            txSteps.publishReply(ChannelConfirmReply.failed(
                    command.sagaId(), "CHANNEL_CONFIRM_FAILED", e.getMessage()));
            return;
        }

        txSteps.publishReply(ChannelConfirmReply.ok(command.sagaId()));
        log.info("channelConfirm: sagaId={} eventId={}", command.sagaId(), eventId);
    }
}
