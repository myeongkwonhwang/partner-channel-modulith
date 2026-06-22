package io.github.orange2652.partner.channel.saga.ordr.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import io.github.orange2652.partner.channel.saga.ordr.domain.SagaState;
import io.github.orange2652.partner.channel.saga.ordr.domain.SagaStateRepository;
import io.github.orange2652.partner.channel.shared.event.ordr.OrderReceivedEvent;
import io.github.orange2652.partner.channel.shared.event.ordr.UnconfirmedOrderCommand;
import java.time.Instant;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;

/**
 * {@link OrderSagaStarter} application 단위 테스트 — Port/협력자 mock (헥사 application 계층 전략, 프레임워크 부팅 0).
 *
 * <p>검증: ① 멱등 신규(true)면 saga_state 를 RUNNING·UNCONFIRMED_ORDER·A1_ORDER·correlationKey 로 저장하고
 * step1 {@link UnconfirmedOrderCommand} 를 같은 sagaId 로 발행한다, ② 멱등 중복(false)면 저장·발행 둘 다 하지 않는다
 * (saga 인스턴스 중복 생성 차단 — R3). {@code @ApplicationModuleListener} 의 비동기·Tx·outbox dispatch 배선은 본
 * 단위 테스트 범위 밖이며 이후 {@code @ApplicationModuleTest} 로 검증한다.</p>
 */
@ExtendWith(MockitoExtension.class)
class OrderSagaStarterTest {

    private static final String CHANNEL = "TOSS";
    private static final String EXT_ID = "o-1";
    private static final String RAW = "{\"orderProductId\":\"o-1\",\"status\":\"PAID\"}";
    private static final String CORRELATION = "TOSS:o-1";

    @Mock
    private SagaIdempotencyGuard idempotencyGuard;

    @Mock
    private SagaStateRepository sagaStateRepository;

    @Mock
    private ApplicationEventPublisher events;

    private OrderReceivedEvent event() {
        return new OrderReceivedEvent(CHANNEL, EXT_ID, Instant.parse("2026-06-19T01:00:00Z"),
                Instant.parse("2026-06-19T01:31:00Z"), RAW);
    }

    @Test
    void 멱등_신규면_saga_를_시작하고_step1_command_를_발행한다() {
        // given — 처음 보는 주문
        given(idempotencyGuard.markIfFirst("sagaStart", CORRELATION)).willReturn(true);
        OrderSagaStarter starter = new OrderSagaStarter(idempotencyGuard, sagaStateRepository, events);

        // when
        starter.onOrderReceived(event());

        // then — saga_state 저장 내용
        ArgumentCaptor<SagaState> stateCaptor = ArgumentCaptor.forClass(SagaState.class);
        verify(sagaStateRepository).save(stateCaptor.capture());
        SagaState saved = stateCaptor.getValue();
        assertThat(saved.sagaType()).isEqualTo("A1_ORDER");
        assertThat(saved.correlationKey()).isEqualTo(CORRELATION);
        assertThat(saved.currentStep()).isEqualTo("UNCONFIRMED_ORDER");
        assertThat(saved.status()).isEqualTo("RUNNING");
        assertThat(saved.payload()).isEqualTo(RAW);
        assertThat(saved.sagaId()).isNotNull();

        // then — step1 command 발행 (saga_state 와 같은 sagaId)
        ArgumentCaptor<UnconfirmedOrderCommand> cmdCaptor = ArgumentCaptor.forClass(UnconfirmedOrderCommand.class);
        verify(events).publishEvent(cmdCaptor.capture());
        UnconfirmedOrderCommand cmd = cmdCaptor.getValue();
        assertThat(cmd.sagaId()).isEqualTo(saved.sagaId());
        assertThat(cmd.channel()).isEqualTo(CHANNEL);
        assertThat(cmd.externalOrderProductId()).isEqualTo(EXT_ID);
        assertThat(cmd.raw()).isEqualTo(RAW);
    }

    @Test
    void 멱등_중복이면_저장도_발행도_하지_않는다() {
        // given — 이미 시작된 주문
        given(idempotencyGuard.markIfFirst("sagaStart", CORRELATION)).willReturn(false);
        OrderSagaStarter starter = new OrderSagaStarter(idempotencyGuard, sagaStateRepository, events);

        // when
        starter.onOrderReceived(event());

        // then — saga 인스턴스 중복 생성 차단
        verify(sagaStateRepository, never()).save(any());
        verify(events, never()).publishEvent(any());
    }
}
