package io.github.orange2652.partner.channel.adapter.ordr.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.willThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import io.github.orange2652.partner.channel.adapter.ordr.domain.ManualCancelRequiredException;
import io.github.orange2652.partner.channel.adapter.ordr.domain.UnsupportedChannelException;
import io.github.orange2652.partner.channel.shared.domain.CancelReason;
import io.github.orange2652.partner.channel.shared.domain.Channel;
import io.github.orange2652.partner.channel.shared.domain.DeliveryPenaltyCharger;
import io.github.orange2652.partner.channel.shared.event.ordr.ChannelConfirmCompensated;
import io.github.orange2652.partner.channel.shared.event.ordr.CompensateChannelConfirmRequested;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;

/**
 * {@link ChannelConfirmCompensationHandler} application 단위 테스트 — Registry/협력자 mock.
 *
 * <p>핵심: cancel 은 사전 GET 가드로 멱등이라 <b>mark-after-success</b>(외부 cancel → mark → reply). Port 미등록·영구
 * 불가는 MANUAL_REQUIRED, transient 실패는 FAILED 로 구분 발행. 실패 경로는 mark 하지 않아 재시도가 막히지 않는다.</p>
 */
@ExtendWith(MockitoExtension.class)
class ChannelConfirmCompensationHandlerTest {

    private static final UUID SAGA_ID = UUID.fromString("00000000-0000-0000-0000-0000000000d4");
    private static final String EXT_ID = "o-1";
    private static final String EVENT_ID = "TOSS:o-1";
    private static final String REASON = "CHANNEL_CONFIRM_FAILED:x";

    @Mock
    private CompensationPortRegistry registry;

    @Mock
    private AdapterTxSteps txSteps;

    @Mock
    private CompensationPort tossPort;

    private CompensateChannelConfirmRequested command(String channelCode) {
        return new CompensateChannelConfirmRequested(
                SAGA_ID, channelCode, EXT_ID, REASON, DeliveryPenaltyCharger.MERCHANT);
    }

    private ChannelConfirmCompensationHandler handler() {
        return new ChannelConfirmCompensationHandler(registry, txSteps);
    }

    @Test
    void cancel_성공_후_마킹하고_OK_를_발행한다() {
        given(registry.find(Channel.TOSS)).willReturn(Optional.of(tossPort));
        given(txSteps.mark("compensate:channelConfirm", EVENT_ID)).willReturn(true);

        handler().onCompensateChannelConfirm(command("TOSS"));

        // 순서: cancel(외부) → mark(성공 후) → publishReply
        ArgumentCaptor<CancelReason> reasonCaptor = ArgumentCaptor.forClass(CancelReason.class);
        InOrder inOrder = Mockito.inOrder(tossPort, txSteps);
        inOrder.verify(tossPort).cancel(eq(EXT_ID), reasonCaptor.capture());
        inOrder.verify(txSteps).mark("compensate:channelConfirm", EVENT_ID);
        ArgumentCaptor<ChannelConfirmCompensated> captor = ArgumentCaptor.forClass(ChannelConfirmCompensated.class);
        inOrder.verify(txSteps).publishReply(captor.capture());
        assertThat(captor.getValue().result()).isEqualTo(ChannelConfirmCompensated.Result.OK);
        // 귀책(MERCHANT)이 event → CancelReason 으로 전달됨
        assertThat(reasonCaptor.getValue().charger()).isEqualTo(DeliveryPenaltyCharger.MERCHANT);
        assertThat(reasonCaptor.getValue().reason()).isEqualTo(REASON);
    }

    @Test
    void cancel_성공해도_멱등_중복이면_OK_를_발행하지_않는다() {
        given(registry.find(Channel.TOSS)).willReturn(Optional.of(tossPort));
        given(txSteps.mark("compensate:channelConfirm", EVENT_ID)).willReturn(false);

        handler().onCompensateChannelConfirm(command("TOSS"));

        verify(tossPort).cancel(eq(EXT_ID), any(CancelReason.class));
        verify(txSteps, never()).publishReply(any());
    }

    @Test
    void Port_미등록이면_cancel_없이_MANUAL_REQUIRED_를_발행한다() {
        given(registry.find(Channel.TOSS)).willReturn(Optional.empty());

        handler().onCompensateChannelConfirm(command("TOSS"));

        ArgumentCaptor<ChannelConfirmCompensated> captor = ArgumentCaptor.forClass(ChannelConfirmCompensated.class);
        verify(txSteps).publishReply(captor.capture());
        assertThat(captor.getValue().result()).isEqualTo(ChannelConfirmCompensated.Result.MANUAL_REQUIRED);
        verify(txSteps, never()).mark(any(), any());
    }

    @Test
    void cancel_이_ManualCancelRequired_면_MANUAL_REQUIRED_를_발행하고_마킹하지_않는다() {
        given(registry.find(Channel.TOSS)).willReturn(Optional.of(tossPort));
        willThrow(new ManualCancelRequiredException(EXT_ID, "FAQ 모순")).given(tossPort).cancel(eq(EXT_ID), any(CancelReason.class));

        handler().onCompensateChannelConfirm(command("TOSS"));

        ArgumentCaptor<ChannelConfirmCompensated> captor = ArgumentCaptor.forClass(ChannelConfirmCompensated.class);
        verify(txSteps).publishReply(captor.capture());
        assertThat(captor.getValue().result()).isEqualTo(ChannelConfirmCompensated.Result.MANUAL_REQUIRED);
        verify(txSteps, never()).mark(any(), any());
    }

    @Test
    void cancel_이_transient_실패면_FAILED_를_발행하고_마킹하지_않는다() {
        given(registry.find(Channel.TOSS)).willReturn(Optional.of(tossPort));
        willThrow(new IllegalStateException("5xx")).given(tossPort).cancel(eq(EXT_ID), any(CancelReason.class));

        handler().onCompensateChannelConfirm(command("TOSS"));

        ArgumentCaptor<ChannelConfirmCompensated> captor = ArgumentCaptor.forClass(ChannelConfirmCompensated.class);
        verify(txSteps).publishReply(captor.capture());
        assertThat(captor.getValue().result()).isEqualTo(ChannelConfirmCompensated.Result.FAILED);
        verify(txSteps, never()).mark(any(), any());
    }

    @Test
    void 지원하지_않는_채널이면_UnsupportedChannelException() {
        assertThatThrownBy(() -> handler().onCompensateChannelConfirm(command("UNKNOWN")))
                .isInstanceOf(UnsupportedChannelException.class)
                .hasMessageContaining("UNKNOWN");

        verify(txSteps, never()).mark(any(), any());
        verify(txSteps, never()).publishReply(any());
    }
}
