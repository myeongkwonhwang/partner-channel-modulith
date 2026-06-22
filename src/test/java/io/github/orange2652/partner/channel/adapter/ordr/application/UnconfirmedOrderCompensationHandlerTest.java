package io.github.orange2652.partner.channel.adapter.ordr.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import io.github.orange2652.partner.channel.adapter.ordr.domain.StagingOrder;
import io.github.orange2652.partner.channel.adapter.ordr.domain.StagingOrderRepository;
import io.github.orange2652.partner.channel.shared.event.ordr.CompensateUnconfirmedOrderRequested;
import io.github.orange2652.partner.channel.shared.event.ordr.UnconfirmedOrderCompensated;
import java.time.LocalDateTime;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;

/**
 * {@link UnconfirmedOrderCompensationHandler} application 단위 테스트 — Port mock.
 *
 * <p>검증: ① 멱등 신규 + staging 존재 → CANCELED 전이 저장 + OK 발행, ② staging 부재 → 저장 없이 OK 발행(취소 목표
 * 충족), ③ 멱등 중복 → 조회·저장·발행 안 함.</p>
 */
@ExtendWith(MockitoExtension.class)
class UnconfirmedOrderCompensationHandlerTest {

    private static final UUID SAGA_ID = UUID.fromString("00000000-0000-0000-0000-0000000000d4");
    private static final String CHANNEL = "TOSS";
    private static final String EXT_ID = "o-1";
    private static final String EVENT_ID = "TOSS:o-1";

    @Mock
    private AdapterIdempotencyGuard idempotencyGuard;

    @Mock
    private StagingOrderRepository stagingOrderRepository;

    @Mock
    private ApplicationEventPublisher events;

    private CompensateUnconfirmedOrderRequested command() {
        return new CompensateUnconfirmedOrderRequested(SAGA_ID, CHANNEL, EXT_ID, "COMPENSATE_AFTER_CHANNEL_CANCEL");
    }

    private UnconfirmedOrderCompensationHandler handler() {
        return new UnconfirmedOrderCompensationHandler(idempotencyGuard, stagingOrderRepository, events);
    }

    @Test
    void 멱등_신규_이고_staging_존재면_CANCELED_저장하고_OK_발행() {
        given(idempotencyGuard.markIfFirst("compensate:unconfirmedOrder", EVENT_ID)).willReturn(true);
        LocalDateTime now = LocalDateTime.of(2026, 6, 22, 10, 0, 0);
        given(stagingOrderRepository.findByChannelAndExternalOrderProductId(CHANNEL, EXT_ID))
                .willReturn(Optional.of(new StagingOrder(7L, CHANNEL, EXT_ID, "{}", "ACTIVE", now, now)));

        handler().onCompensateUnconfirmedOrder(command());

        ArgumentCaptor<StagingOrder> stagingCaptor = ArgumentCaptor.forClass(StagingOrder.class);
        verify(stagingOrderRepository).save(stagingCaptor.capture());
        assertThat(stagingCaptor.getValue().isCanceled()).isTrue();

        ArgumentCaptor<UnconfirmedOrderCompensated> replyCaptor =
                ArgumentCaptor.forClass(UnconfirmedOrderCompensated.class);
        verify(events).publishEvent(replyCaptor.capture());
        assertThat(replyCaptor.getValue().result()).isEqualTo(UnconfirmedOrderCompensated.Result.OK);
    }

    @Test
    void staging_부재여도_저장없이_OK_발행() {
        given(idempotencyGuard.markIfFirst("compensate:unconfirmedOrder", EVENT_ID)).willReturn(true);
        given(stagingOrderRepository.findByChannelAndExternalOrderProductId(CHANNEL, EXT_ID))
                .willReturn(Optional.empty());

        handler().onCompensateUnconfirmedOrder(command());

        verify(stagingOrderRepository, never()).save(any());
        verify(events).publishEvent(any(UnconfirmedOrderCompensated.class));
    }

    @Test
    void 멱등_중복이면_조회도_저장도_발행도_안_한다() {
        given(idempotencyGuard.markIfFirst("compensate:unconfirmedOrder", EVENT_ID)).willReturn(false);

        handler().onCompensateUnconfirmedOrder(command());

        verify(stagingOrderRepository, never()).findByChannelAndExternalOrderProductId(any(), any());
        verify(stagingOrderRepository, never()).save(any());
        verify(events, never()).publishEvent(any());
    }
}
