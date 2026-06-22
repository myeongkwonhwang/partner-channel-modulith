package io.github.orange2652.partner.channel.saga.ordr.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import io.github.orange2652.partner.channel.saga.ordr.domain.SagaState;
import io.github.orange2652.partner.channel.saga.ordr.domain.SagaStateNotFoundException;
import io.github.orange2652.partner.channel.saga.ordr.domain.SagaStateRepository;
import io.github.orange2652.partner.channel.shared.event.ordr.UnconfirmedOrderReply;
import io.github.orange2652.partner.channel.shared.event.ordr.ValidateCommand;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;

/**
 * {@link UnconfirmedReplyHandler} application 단위 테스트 — Port mock (헥사 application 계층 전략).
 *
 * <p>검증: ① OK + saga 존재 + 멱등 신규 → VALIDATE advance 저장 + step2 {@link ValidateCommand} 발행
 * (channel=correlationKey 파싱, raw=payload), ② 멱등 중복 → advance·발행 안 함, ③ saga 부재 →
 * {@link SagaStateNotFoundException}, ④ FAILED → 조회·advance·발행 안 함(보상은 이후).</p>
 */
@ExtendWith(MockitoExtension.class)
class UnconfirmedReplyHandlerTest {

    private static final UUID SAGA_ID = UUID.fromString("00000000-0000-0000-0000-0000000000a1");
    private static final String CORRELATION = "TOSS:o-1";
    private static final String PAYLOAD = "{\"orderProductId\":\"o-1\"}";

    @Mock
    private SagaIdempotencyGuard idempotencyGuard;

    @Mock
    private SagaStateRepository sagaStateRepository;

    @Mock
    private ApplicationEventPublisher events;

    private SagaState runningAtStep1() {
        return SagaState.start(SAGA_ID, A1OrderSaga.TYPE, CORRELATION, A1OrderSaga.STEP_UNCONFIRMED_ORDER, PAYLOAD);
    }

    private UnconfirmedReplyHandler handler() {
        return new UnconfirmedReplyHandler(idempotencyGuard, sagaStateRepository, events);
    }

    @Test
    void OK_이고_멱등_신규면_VALIDATE_advance_하고_ValidateCommand_를_발행한다() {
        // given
        given(sagaStateRepository.findBySagaId(SAGA_ID)).willReturn(Optional.of(runningAtStep1()));
        given(idempotencyGuard.markIfFirst("unconfirmedOrderReply", CORRELATION)).willReturn(true);

        // when
        handler().onUnconfirmedOrderReply(UnconfirmedOrderReply.ok(SAGA_ID, 99L));

        // then — advance(VALIDATE)
        ArgumentCaptor<SagaState> stateCaptor = ArgumentCaptor.forClass(SagaState.class);
        verify(sagaStateRepository).save(stateCaptor.capture());
        assertThat(stateCaptor.getValue().currentStep()).isEqualTo("VALIDATE");

        // then — step2 ValidateCommand (channel=correlationKey 파싱, raw=payload)
        ArgumentCaptor<ValidateCommand> cmdCaptor = ArgumentCaptor.forClass(ValidateCommand.class);
        verify(events).publishEvent(cmdCaptor.capture());
        ValidateCommand cmd = cmdCaptor.getValue();
        assertThat(cmd.sagaId()).isEqualTo(SAGA_ID);
        assertThat(cmd.channel()).isEqualTo("TOSS");
        assertThat(cmd.raw()).isEqualTo(PAYLOAD);
    }

    @Test
    void OK_여도_멱등_중복이면_advance_도_발행_도_안_한다() {
        given(sagaStateRepository.findBySagaId(SAGA_ID)).willReturn(Optional.of(runningAtStep1()));
        given(idempotencyGuard.markIfFirst("unconfirmedOrderReply", CORRELATION)).willReturn(false);

        handler().onUnconfirmedOrderReply(UnconfirmedOrderReply.ok(SAGA_ID, 99L));

        verify(sagaStateRepository, never()).save(any());
        verify(events, never()).publishEvent(any());
    }

    @Test
    void saga_가_없으면_SagaStateNotFoundException() {
        given(sagaStateRepository.findBySagaId(SAGA_ID)).willReturn(Optional.empty());

        assertThatThrownBy(() -> handler().onUnconfirmedOrderReply(UnconfirmedOrderReply.ok(SAGA_ID, 99L)))
                .isInstanceOf(SagaStateNotFoundException.class)
                .hasMessageContaining(SAGA_ID.toString());
        verify(events, never()).publishEvent(any());
    }

    @Test
    void FAILED_면_조회도_advance도_발행도_안_한다() {
        handler().onUnconfirmedOrderReply(UnconfirmedOrderReply.failed(SAGA_ID, "STAGING_FAIL", "x"));

        verify(sagaStateRepository, never()).findBySagaId(any());
        verify(sagaStateRepository, never()).save(any());
        verify(events, never()).publishEvent(any());
    }
}
