package io.github.orange2652.partner.channel.saga.ordr.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.github.orange2652.partner.channel.saga.ordr.domain.SagaState;
import io.github.orange2652.partner.channel.saga.ordr.domain.SagaStateRepository;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.time.Duration;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

/**
 * {@link SagaTimeoutScanner} 단위 테스트 — application 계층, Port mock(R5a ③ step 분기 검증). 외부 GET/DB 없이
 * stuck saga 가 step 별로 올바른 복구 액션으로 분기하는지만 단언한다.
 */
class SagaTimeoutScannerTest {

    @Mock
    private SagaStateRepository sagaStateRepository;
    @Mock
    private OrderSagaCompensator compensator;

    private SagaTimeoutScanner scanner;
    private AutoCloseable mocks;

    @BeforeEach
    void setUp() {
        mocks = MockitoAnnotations.openMocks(this);
        SagaTimeoutProperties properties = new SagaTimeoutProperties(Duration.ofMinutes(30), Duration.ofHours(1));
        scanner = new SagaTimeoutScanner(sagaStateRepository, properties, compensator, new SimpleMeterRegistry());
    }

    private SagaState running(String step) {
        return SagaState.start(UUID.randomUUID(), A1OrderSaga.TYPE, "NAVER:ORD-" + step, step, "{}");
    }

    private void stubStuck(List<SagaState> running, List<SagaState> compensating) {
        when(sagaStateRepository.findStuck(eq(A1OrderSaga.STATUS_RUNNING), any())).thenReturn(running);
        when(sagaStateRepository.findStuck(eq(A1OrderSaga.STATUS_COMPENSATING), any())).thenReturn(compensating);
    }

    @Test
    void step1_2_stuck_은_보상_트리거() {
        SagaState stuck = running(A1OrderSaga.STEP_VALIDATE);
        stubStuck(List.of(stuck), List.of());

        scanner.scan();

        verify(compensator).start(stuck.sagaId(), "TIMEOUT:" + A1OrderSaga.STEP_VALIDATE);
        verify(sagaStateRepository, never()).save(any());
    }

    @Test
    void step3_channelConfirm_stuck_은_외부GET_이연이라_자동조치_보류() {
        SagaState stuck = running(A1OrderSaga.STEP_CHANNEL_CONFIRM);
        stubStuck(List.of(stuck), List.of());

        scanner.scan();

        verify(compensator, never()).start(any(), any());
        verify(sagaStateRepository, never()).save(any());
    }

    @Test
    void step4_Pivot_stuck_은_PENDING_RECONCILIATION() {
        SagaState stuck = running(A1OrderSaga.STEP_CONFIRMED_ORDER);
        stubStuck(List.of(stuck), List.of());

        scanner.scan();

        ArgumentCaptor<SagaState> saved = ArgumentCaptor.forClass(SagaState.class);
        verify(sagaStateRepository).save(saved.capture());
        assertThat(saved.getValue().status()).isEqualTo(A1OrderSaga.STATUS_PENDING_RECONCILIATION);
        verify(compensator, never()).start(any(), any());
    }

    @Test
    void compensating_stuck_은_COMPENSATION_STUCK_terminal() {
        SagaState stuck = SagaState.start(UUID.randomUUID(), A1OrderSaga.TYPE, "NAVER:ORD-C",
                A1OrderSaga.STEP_CHANNEL_CONFIRM, "{}").withStatus(A1OrderSaga.STATUS_COMPENSATING);
        stubStuck(List.of(), List.of(stuck));

        scanner.scan();

        ArgumentCaptor<SagaState> saved = ArgumentCaptor.forClass(SagaState.class);
        verify(sagaStateRepository).save(saved.capture());
        assertThat(saved.getValue().status()).isEqualTo(A1OrderSaga.STATUS_COMPENSATION_STUCK);
    }
}
