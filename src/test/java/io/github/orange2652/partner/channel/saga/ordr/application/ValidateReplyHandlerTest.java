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
import io.github.orange2652.partner.channel.shared.event.ordr.ChannelConfirmCommand;
import io.github.orange2652.partner.channel.shared.event.ordr.ValidateReply;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;

/**
 * {@link ValidateReplyHandler} application 단위 테스트 — Port mock.
 *
 * <p>검증: ① PASSED + saga 존재 + 멱등 신규 → CHANNEL_CONFIRM advance + ChannelConfirmCommand 발행(channel/
 * externalOrderProductId 를 correlationKey 에서 소싱), ② 멱등 중복 → advance·발행 안 함, ③ saga 부재 →
 * {@link SagaStateNotFoundException}, ④ REJECTED → 조회·advance·발행 안 함(R4 보상 이연, 미소비 유지).</p>
 */
@ExtendWith(MockitoExtension.class)
class ValidateReplyHandlerTest {

    private static final UUID SAGA_ID = UUID.fromString("00000000-0000-0000-0000-0000000000b2");
    private static final String CORRELATION = "TOSS:o-1";

    @Mock
    private SagaIdempotencyGuard idempotencyGuard;

    @Mock
    private SagaStateRepository sagaStateRepository;

    @Mock
    private ApplicationEventPublisher events;

    @Mock
    private OrderSagaCompensator compensator;

    private SagaState runningAtValidate() {
        return SagaState.start(SAGA_ID, A1OrderSaga.TYPE, CORRELATION, A1OrderSaga.STEP_VALIDATE, "{}");
    }

    private ValidateReplyHandler handler() {
        return new ValidateReplyHandler(idempotencyGuard, sagaStateRepository, events, compensator);
    }

    @Test
    void PASSED_이고_멱등_신규면_CHANNEL_CONFIRM_advance_후_ChannelConfirmCommand_를_발행한다() {
        given(sagaStateRepository.findBySagaId(SAGA_ID)).willReturn(Optional.of(runningAtValidate()));
        given(idempotencyGuard.markIfFirst("validateReply", CORRELATION)).willReturn(true);

        handler().onValidateReply(ValidateReply.passed(SAGA_ID));

        ArgumentCaptor<SagaState> stateCaptor = ArgumentCaptor.forClass(SagaState.class);
        verify(sagaStateRepository).save(stateCaptor.capture());
        assertThat(stateCaptor.getValue().currentStep()).isEqualTo("CHANNEL_CONFIRM");

        ArgumentCaptor<ChannelConfirmCommand> cmdCaptor = ArgumentCaptor.forClass(ChannelConfirmCommand.class);
        verify(events).publishEvent(cmdCaptor.capture());
        ChannelConfirmCommand cmd = cmdCaptor.getValue();
        assertThat(cmd.sagaId()).isEqualTo(SAGA_ID);
        assertThat(cmd.channel()).isEqualTo("TOSS");
        assertThat(cmd.externalOrderProductId()).isEqualTo("o-1");
    }

    @Test
    void PASSED_여도_멱등_중복이면_advance도_발행도_하지_않는다() {
        given(sagaStateRepository.findBySagaId(SAGA_ID)).willReturn(Optional.of(runningAtValidate()));
        given(idempotencyGuard.markIfFirst("validateReply", CORRELATION)).willReturn(false);

        handler().onValidateReply(ValidateReply.passed(SAGA_ID));

        verify(sagaStateRepository, never()).save(any());
        verify(events, never()).publishEvent(any());
    }

    @Test
    void saga_가_없으면_SagaStateNotFoundException() {
        given(sagaStateRepository.findBySagaId(SAGA_ID)).willReturn(Optional.empty());

        assertThatThrownBy(() -> handler().onValidateReply(ValidateReply.passed(SAGA_ID)))
                .isInstanceOf(SagaStateNotFoundException.class)
                .hasMessageContaining(SAGA_ID.toString());
    }

    @Test
    void REJECTED_면_직접_advance하지_않고_보상을_시작한다() {
        // R4: PAID 사실이라 무페널티 출구 없음 → seller-cancel + staging (귀책 MERCHANT). 전이/발행은 compensator 책임.
        handler().onValidateReply(ValidateReply.rejected(SAGA_ID, "UNSUPPORTED_CHANNEL", "x"));

        verify(sagaStateRepository, never()).findBySagaId(any());
        verify(sagaStateRepository, never()).save(any());
        verify(events, never()).publishEvent(any());

        ArgumentCaptor<String> reasonCaptor = ArgumentCaptor.forClass(String.class);
        verify(compensator).start(eq(SAGA_ID), reasonCaptor.capture());
        assertThat(reasonCaptor.getValue())
                .contains("VALIDATE_REJECTED")
                .contains("UNSUPPORTED_CHANNEL")
                .contains("MERCHANT");
    }
}
