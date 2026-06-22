package io.github.orange2652.partner.channel.saga.ordr.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import io.github.orange2652.partner.channel.saga.ordr.domain.SagaState;
import io.github.orange2652.partner.channel.saga.ordr.domain.SagaStateRepository;
import io.github.orange2652.partner.channel.shared.event.ordr.ChannelConfirmCompensated;
import io.github.orange2652.partner.channel.shared.event.ordr.CompensateUnconfirmedOrderRequested;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;

/**
 * {@link ChannelConfirmCompensatedHandler} application 단위 테스트 — Port mock.
 *
 * <p>검증: ① OK + 멱등 신규 → staging 취소 요청(CompensateUnconfirmedOrderRequested) 발행, ② OK 라도 멱등 중복 →
 * 발행 안 함, ③ MANUAL_REQUIRED → PENDING_MANUAL_CANCEL terminal 전이, ④ FAILED → COMPENSATING 유지(전이·발행 없음).</p>
 */
@ExtendWith(MockitoExtension.class)
class ChannelConfirmCompensatedHandlerTest {

    private static final UUID SAGA_ID = UUID.fromString("00000000-0000-0000-0000-0000000000d4");
    private static final String CORRELATION = "TOSS:o-1";

    @Mock
    private SagaIdempotencyGuard idempotencyGuard;

    @Mock
    private SagaStateRepository sagaStateRepository;

    @Mock
    private ApplicationEventPublisher events;

    private SagaState compensating() {
        return SagaState.start(SAGA_ID, A1OrderSaga.TYPE, CORRELATION, A1OrderSaga.STEP_CHANNEL_CONFIRM, "{}")
                .withStatus(A1OrderSaga.STATUS_COMPENSATING);
    }

    private ChannelConfirmCompensatedHandler handler() {
        return new ChannelConfirmCompensatedHandler(idempotencyGuard, sagaStateRepository, events);
    }

    @Test
    void OK_이고_멱등_신규면_staging_취소_요청을_발행한다() {
        given(sagaStateRepository.findBySagaId(SAGA_ID)).willReturn(Optional.of(compensating()));
        given(idempotencyGuard.markIfFirst("channelConfirmCompensated", CORRELATION)).willReturn(true);

        handler().onChannelConfirmCompensated(ChannelConfirmCompensated.ok(SAGA_ID));

        ArgumentCaptor<CompensateUnconfirmedOrderRequested> captor =
                ArgumentCaptor.forClass(CompensateUnconfirmedOrderRequested.class);
        verify(events).publishEvent(captor.capture());
        CompensateUnconfirmedOrderRequested cmd = captor.getValue();
        assertThat(cmd.channel()).isEqualTo("TOSS");
        assertThat(cmd.externalOrderProductId()).isEqualTo("o-1");
    }

    @Test
    void OK_여도_멱등_중복이면_발행하지_않는다() {
        given(sagaStateRepository.findBySagaId(SAGA_ID)).willReturn(Optional.of(compensating()));
        given(idempotencyGuard.markIfFirst("channelConfirmCompensated", CORRELATION)).willReturn(false);

        handler().onChannelConfirmCompensated(ChannelConfirmCompensated.ok(SAGA_ID));

        verify(events, never()).publishEvent(any());
    }

    @Test
    void MANUAL_REQUIRED_면_PENDING_MANUAL_CANCEL_로_전이한다() {
        given(sagaStateRepository.findBySagaId(SAGA_ID)).willReturn(Optional.of(compensating()));

        handler().onChannelConfirmCompensated(ChannelConfirmCompensated.manualRequired(SAGA_ID));

        ArgumentCaptor<SagaState> captor = ArgumentCaptor.forClass(SagaState.class);
        verify(sagaStateRepository).save(captor.capture());
        assertThat(captor.getValue().status()).isEqualTo("PENDING_MANUAL_CANCEL");
        verify(events, never()).publishEvent(any());
    }

    @Test
    void FAILED_면_전이도_발행도_하지_않고_COMPENSATING_을_유지한다() {
        given(sagaStateRepository.findBySagaId(SAGA_ID)).willReturn(Optional.of(compensating()));

        handler().onChannelConfirmCompensated(ChannelConfirmCompensated.failed(SAGA_ID));

        verify(sagaStateRepository, never()).save(any());
        verify(events, never()).publishEvent(any());
    }
}
