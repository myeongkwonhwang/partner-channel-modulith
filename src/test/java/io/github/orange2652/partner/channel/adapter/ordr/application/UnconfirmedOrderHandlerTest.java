package io.github.orange2652.partner.channel.adapter.ordr.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import io.github.orange2652.partner.channel.adapter.ordr.domain.StagingOrder;
import io.github.orange2652.partner.channel.adapter.ordr.domain.StagingOrderRepository;
import io.github.orange2652.partner.channel.shared.event.ordr.UnconfirmedOrderCommand;
import io.github.orange2652.partner.channel.shared.event.ordr.UnconfirmedOrderReply;
import java.time.LocalDateTime;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;

/**
 * {@link UnconfirmedOrderHandler} application 단위 테스트 — Port/협력자 mock (헥사 application 계층 전략).
 *
 * <p>검증: ① 멱등 신규(true)면 staging 을 ACTIVE 로 저장하고 {@link UnconfirmedOrderReply#ok} 를 같은 sagaId·
 * stagingId 로 발행, ② 멱등 중복(false)면 저장·발행 둘 다 안 함(원본이 이미 처리·reply — R3). staging 저장 키는
 * command 가 운반한 {@code externalOrderProductId} 를 그대로 쓴다(raw 재파싱 없음).</p>
 */
@ExtendWith(MockitoExtension.class)
class UnconfirmedOrderHandlerTest {

    private static final UUID SAGA_ID = UUID.fromString("00000000-0000-0000-0000-0000000000a1");
    private static final String CHANNEL = "TOSS";
    private static final String EXT_ID = "o-1";
    private static final String RAW = "{\"orderProductId\":\"o-1\",\"status\":\"PAID\"}";
    private static final long STAGING_ID = 99L;

    @Mock
    private AdapterIdempotencyGuard idempotencyGuard;

    @Mock
    private StagingOrderRepository stagingOrderRepository;

    @Mock
    private ApplicationEventPublisher events;

    private UnconfirmedOrderCommand command() {
        return new UnconfirmedOrderCommand(SAGA_ID, CHANNEL, EXT_ID, RAW);
    }

    @Test
    void 멱등_신규면_staging_을_적재하고_reply_OK_를_발행한다() {
        // given — 처음 보는 주문, 저장 시 id 채번된 staging 반환
        given(idempotencyGuard.markIfFirst("unconfirmedOrder", "TOSS:o-1")).willReturn(true);
        LocalDateTime now = LocalDateTime.of(2026, 6, 19, 10, 0, 0);
        given(stagingOrderRepository.save(any())).willReturn(
                new StagingOrder(STAGING_ID, CHANNEL, EXT_ID, RAW, "ACTIVE", now, now));
        UnconfirmedOrderHandler handler = new UnconfirmedOrderHandler(idempotencyGuard, stagingOrderRepository, events);

        // when
        handler.onUnconfirmedOrder(command());

        // then — staging 저장 내용(command 의 externalOrderProductId 사용, ACTIVE)
        ArgumentCaptor<StagingOrder> stagingCaptor = ArgumentCaptor.forClass(StagingOrder.class);
        verify(stagingOrderRepository).save(stagingCaptor.capture());
        StagingOrder toSave = stagingCaptor.getValue();
        assertThat(toSave.channel()).isEqualTo(CHANNEL);
        assertThat(toSave.externalOrderProductId()).isEqualTo(EXT_ID);
        assertThat(toSave.raw()).isEqualTo(RAW);
        assertThat(toSave.status()).isEqualTo("ACTIVE");

        // then — reply OK (sagaId + 채번된 stagingId)
        ArgumentCaptor<UnconfirmedOrderReply> replyCaptor = ArgumentCaptor.forClass(UnconfirmedOrderReply.class);
        verify(events).publishEvent(replyCaptor.capture());
        UnconfirmedOrderReply reply = replyCaptor.getValue();
        assertThat(reply.sagaId()).isEqualTo(SAGA_ID);
        assertThat(reply.result()).isEqualTo(UnconfirmedOrderReply.Result.OK);
        assertThat(reply.stagingId()).isEqualTo(STAGING_ID);
    }

    @Test
    void 멱등_중복이면_저장도_발행도_하지_않는다() {
        // given — 이미 처리된 주문
        given(idempotencyGuard.markIfFirst("unconfirmedOrder", "TOSS:o-1")).willReturn(false);
        UnconfirmedOrderHandler handler = new UnconfirmedOrderHandler(idempotencyGuard, stagingOrderRepository, events);

        // when
        handler.onUnconfirmedOrder(command());

        // then
        verify(stagingOrderRepository, never()).save(any());
        verify(events, never()).publishEvent(any());
    }
}
