package io.github.orange2652.partner.channel.saga.ordr.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import io.github.orange2652.partner.channel.saga.ordr.domain.SagaState;
import io.github.orange2652.partner.channel.saga.ordr.domain.SagaStateNotFoundException;
import io.github.orange2652.partner.channel.saga.ordr.domain.SagaStateRepository;
import io.github.orange2652.partner.channel.shared.event.ordr.ChannelConfirmReply;
import io.github.orange2652.partner.channel.shared.event.ordr.ConfirmedOrderCommand;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;

/**
 * {@link ChannelConfirmReplyHandler} application 단위 테스트 — Port mock.
 *
 * <p>검증: ① OK + saga 존재 + 멱등 신규 → CONFIRMED_ORDER advance + ConfirmedOrderCommand 발행(channel=
 * correlationKey, raw=payload), ② 멱등 중복 → advance·발행 안 함, ③ saga 부재 → {@link SagaStateNotFoundException},
 * ④ FAILED → 조회·advance·발행 안 함(R4 보상 이연, 미소비 유지 — step1/step2 와 동일 컨벤션).</p>
 */
@ExtendWith(MockitoExtension.class)
class ChannelConfirmReplyHandlerTest {

    private static final UUID SAGA_ID = UUID.fromString("00000000-0000-0000-0000-0000000000c3");
    private static final String CORRELATION = "TOSS:o-1";
    private static final String PAYLOAD = "{\"orderProductId\":\"o-1\",\"status\":\"PAID\"}";

    @Mock
    private SagaIdempotencyGuard idempotencyGuard;

    @Mock
    private SagaStateRepository sagaStateRepository;

    @Mock
    private ApplicationEventPublisher events;

    @Mock
    private OrderSagaCompensator compensator;

    private SagaState runningAtChannelConfirm() {
        return SagaState.start(SAGA_ID, A1OrderSaga.TYPE, CORRELATION, A1OrderSaga.STEP_CHANNEL_CONFIRM, PAYLOAD);
    }

    private ChannelConfirmReplyHandler handler() {
        return new ChannelConfirmReplyHandler(idempotencyGuard, sagaStateRepository, events, compensator);
    }

    @Test
    void OK_이고_멱등_신규면_CONFIRMED_ORDER_advance_후_ConfirmedOrderCommand_를_발행한다() {
        given(sagaStateRepository.findBySagaId(SAGA_ID)).willReturn(Optional.of(runningAtChannelConfirm()));
        given(idempotencyGuard.markIfFirst("channelConfirmReply", CORRELATION)).willReturn(true);

        handler().onChannelConfirmReply(ChannelConfirmReply.ok(SAGA_ID));

        ArgumentCaptor<SagaState> stateCaptor = ArgumentCaptor.forClass(SagaState.class);
        verify(sagaStateRepository).save(stateCaptor.capture());
        assertThat(stateCaptor.getValue().currentStep()).isEqualTo("CONFIRMED_ORDER");

        ArgumentCaptor<ConfirmedOrderCommand> cmdCaptor = ArgumentCaptor.forClass(ConfirmedOrderCommand.class);
        verify(events).publishEvent(cmdCaptor.capture());
        ConfirmedOrderCommand cmd = cmdCaptor.getValue();
        assertThat(cmd.sagaId()).isEqualTo(SAGA_ID);
        assertThat(cmd.channel()).isEqualTo("TOSS");
        assertThat(cmd.raw()).isEqualTo(PAYLOAD);
    }

    @Test
    void OK_여도_멱등_중복이면_advance도_발행도_하지_않는다() {
        given(sagaStateRepository.findBySagaId(SAGA_ID)).willReturn(Optional.of(runningAtChannelConfirm()));
        given(idempotencyGuard.markIfFirst("channelConfirmReply", CORRELATION)).willReturn(false);

        handler().onChannelConfirmReply(ChannelConfirmReply.ok(SAGA_ID));

        verify(sagaStateRepository, never()).save(any());
        verify(events, never()).publishEvent(any());
    }

    @Test
    void saga_가_없으면_SagaStateNotFoundException() {
        given(sagaStateRepository.findBySagaId(SAGA_ID)).willReturn(Optional.empty());

        assertThatThrownBy(() -> handler().onChannelConfirmReply(ChannelConfirmReply.ok(SAGA_ID)))
                .isInstanceOf(SagaStateNotFoundException.class)
                .hasMessageContaining(SAGA_ID.toString());
    }

    @Test
    void FAILED_면_직접_advance하지_않고_보상을_시작한다() {
        // R4: 외부 통보 실패 → 외부 cancel + staging 취소. 전이/발행은 compensator 책임.
        handler().onChannelConfirmReply(ChannelConfirmReply.failed(SAGA_ID, "CHANNEL_CONFIRM_FAILED", "x"));

        verify(sagaStateRepository, never()).findBySagaId(any());
        verify(sagaStateRepository, never()).save(any());
        verify(events, never()).publishEvent(any());

        ArgumentCaptor<String> reasonCaptor = ArgumentCaptor.forClass(String.class);
        verify(compensator).start(eq(SAGA_ID), reasonCaptor.capture());
        assertThat(reasonCaptor.getValue()).contains("CHANNEL_CONFIRM_FAILED");
    }
}
