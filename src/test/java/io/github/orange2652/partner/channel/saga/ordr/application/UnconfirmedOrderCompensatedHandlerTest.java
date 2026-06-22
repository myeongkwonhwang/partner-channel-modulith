package io.github.orange2652.partner.channel.saga.ordr.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import io.github.orange2652.partner.channel.saga.ordr.domain.SagaState;
import io.github.orange2652.partner.channel.saga.ordr.domain.SagaStateRepository;
import io.github.orange2652.partner.channel.shared.event.ordr.UnconfirmedOrderCompensated;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/**
 * {@link UnconfirmedOrderCompensatedHandler} application 단위 테스트 — Port mock.
 *
 * <p>검증: ① OK + 멱등 신규 → COMPENSATED terminal 전이, ② OK 라도 멱등 중복 → 전이 안 함, ③ FAILED → COMPENSATING
 * 유지(조회·전이 없음).</p>
 */
@ExtendWith(MockitoExtension.class)
class UnconfirmedOrderCompensatedHandlerTest {

    private static final UUID SAGA_ID = UUID.fromString("00000000-0000-0000-0000-0000000000d4");
    private static final String CORRELATION = "TOSS:o-1";

    @Mock
    private SagaIdempotencyGuard idempotencyGuard;

    @Mock
    private SagaStateRepository sagaStateRepository;

    private SagaState compensating() {
        return SagaState.start(SAGA_ID, A1OrderSaga.TYPE, CORRELATION, A1OrderSaga.STEP_CHANNEL_CONFIRM, "{}")
                .withStatus(A1OrderSaga.STATUS_COMPENSATING);
    }

    private UnconfirmedOrderCompensatedHandler handler() {
        return new UnconfirmedOrderCompensatedHandler(idempotencyGuard, sagaStateRepository);
    }

    @Test
    void OK_이고_멱등_신규면_COMPENSATED_로_전이한다() {
        given(sagaStateRepository.findBySagaId(SAGA_ID)).willReturn(Optional.of(compensating()));
        given(idempotencyGuard.markIfFirst("unconfirmedOrderCompensated", CORRELATION)).willReturn(true);

        handler().onUnconfirmedOrderCompensated(UnconfirmedOrderCompensated.ok(SAGA_ID));

        ArgumentCaptor<SagaState> captor = ArgumentCaptor.forClass(SagaState.class);
        verify(sagaStateRepository).save(captor.capture());
        assertThat(captor.getValue().status()).isEqualTo("COMPENSATED");
    }

    @Test
    void OK_여도_멱등_중복이면_전이하지_않는다() {
        given(sagaStateRepository.findBySagaId(SAGA_ID)).willReturn(Optional.of(compensating()));
        given(idempotencyGuard.markIfFirst("unconfirmedOrderCompensated", CORRELATION)).willReturn(false);

        handler().onUnconfirmedOrderCompensated(UnconfirmedOrderCompensated.ok(SAGA_ID));

        verify(sagaStateRepository, never()).save(any());
    }

    @Test
    void FAILED_면_조회도_전이도_하지_않고_COMPENSATING_을_유지한다() {
        handler().onUnconfirmedOrderCompensated(UnconfirmedOrderCompensated.failed(SAGA_ID));

        verify(sagaStateRepository, never()).findBySagaId(any());
        verify(sagaStateRepository, never()).save(any());
    }
}
