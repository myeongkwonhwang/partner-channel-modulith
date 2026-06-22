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
import io.github.orange2652.partner.channel.shared.domain.DeliveryPenaltyCharger;
import io.github.orange2652.partner.channel.shared.event.ordr.CompensateChannelConfirmRequested;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;

/**
 * {@link OrderSagaCompensator} application 단위 테스트 — Port mock.
 *
 * <p>검증: ① 멱등 신규 → COMPENSATING 전이 + CompensateChannelConfirmRequested 발행(channel/externalOrderProductId 를
 * correlationKey 에서 소싱, reason 전달), ② 멱등 중복 → 전이·발행 안 함, ③ saga 부재 → {@link SagaStateNotFoundException}.</p>
 */
@ExtendWith(MockitoExtension.class)
class OrderSagaCompensatorTest {

    private static final UUID SAGA_ID = UUID.fromString("00000000-0000-0000-0000-0000000000d4");
    private static final String CORRELATION = "TOSS:o-1";
    private static final String REASON = "VALIDATE_REJECTED:UNSUPPORTED_CHANNEL:deliveryPenaltyCharger=MERCHANT";

    @Mock
    private SagaIdempotencyGuard idempotencyGuard;

    @Mock
    private SagaStateRepository sagaStateRepository;

    @Mock
    private ApplicationEventPublisher events;

    private SagaState running() {
        return SagaState.start(SAGA_ID, A1OrderSaga.TYPE, CORRELATION, A1OrderSaga.STEP_VALIDATE, "{}");
    }

    private OrderSagaCompensator compensator() {
        return new OrderSagaCompensator(idempotencyGuard, sagaStateRepository, events);
    }

    @Test
    void 멱등_신규면_COMPENSATING_전이_후_외부_cancel_요청을_발행한다() {
        given(sagaStateRepository.findBySagaId(SAGA_ID)).willReturn(Optional.of(running()));
        given(idempotencyGuard.markIfFirst("compensateStart", CORRELATION)).willReturn(true);

        compensator().start(SAGA_ID, REASON);

        ArgumentCaptor<SagaState> stateCaptor = ArgumentCaptor.forClass(SagaState.class);
        verify(sagaStateRepository).save(stateCaptor.capture());
        assertThat(stateCaptor.getValue().status()).isEqualTo("COMPENSATING");

        ArgumentCaptor<CompensateChannelConfirmRequested> cmdCaptor =
                ArgumentCaptor.forClass(CompensateChannelConfirmRequested.class);
        verify(events).publishEvent(cmdCaptor.capture());
        CompensateChannelConfirmRequested cmd = cmdCaptor.getValue();
        assertThat(cmd.sagaId()).isEqualTo(SAGA_ID);
        assertThat(cmd.channel()).isEqualTo("TOSS");
        assertThat(cmd.externalOrderProductId()).isEqualTo("o-1");
        assertThat(cmd.reason()).isEqualTo(REASON);
        assertThat(cmd.deliveryPenaltyCharger()).isEqualTo(DeliveryPenaltyCharger.MERCHANT);
    }

    @Test
    void 멱등_중복이면_전이도_발행도_하지_않는다() {
        given(sagaStateRepository.findBySagaId(SAGA_ID)).willReturn(Optional.of(running()));
        given(idempotencyGuard.markIfFirst("compensateStart", CORRELATION)).willReturn(false);

        compensator().start(SAGA_ID, REASON);

        verify(sagaStateRepository, never()).save(any());
        verify(events, never()).publishEvent(any());
    }

    @Test
    void saga_가_없으면_SagaStateNotFoundException() {
        given(sagaStateRepository.findBySagaId(SAGA_ID)).willReturn(Optional.empty());

        assertThatThrownBy(() -> compensator().start(SAGA_ID, REASON))
                .isInstanceOf(SagaStateNotFoundException.class)
                .hasMessageContaining(SAGA_ID.toString());
    }
}
