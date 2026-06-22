package io.github.orange2652.partner.channel.adapter.ordr.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.willThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import io.github.orange2652.partner.channel.adapter.ordr.domain.ConfirmStrategyNotFoundException;
import io.github.orange2652.partner.channel.adapter.ordr.domain.UnsupportedChannelException;
import io.github.orange2652.partner.channel.shared.domain.Channel;
import io.github.orange2652.partner.channel.shared.event.ordr.ChannelConfirmCommand;
import io.github.orange2652.partner.channel.shared.event.ordr.ChannelConfirmReply;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;

/**
 * {@link ChannelConfirmHandler} application 단위 테스트 — Registry/협력자 mock.
 *
 * <p>핵심은 외부 호출 Tx 분리 순서: ① 채널·전략 resolve(설정 오류는 멱등 소비 전 fail-fast) → ② mark 커밋(외부 전)
 * → ③ confirm(외부) → ④ reply 발행. 단위에서는 협력자 호출 순서/조건으로 이 컨벤션을 검증한다(실제 Tx 경계는
 * {@link AdapterTxSteps} 의 {@code @Transactional} 책임 — @ApplicationModuleTest 증분에서 통합 검증).</p>
 */
@ExtendWith(MockitoExtension.class)
class ChannelConfirmHandlerTest {

    private static final UUID SAGA_ID = UUID.fromString("00000000-0000-0000-0000-0000000000c3");
    private static final String EXT_ID = "o-1";
    private static final String EVENT_ID = "TOSS:o-1";

    @Mock
    private ConfirmStrategyRegistry registry;

    @Mock
    private AdapterTxSteps txSteps;

    @Mock
    private ConfirmStrategy tossStrategy;

    private ChannelConfirmCommand command(String channelCode) {
        return new ChannelConfirmCommand(SAGA_ID, channelCode, EXT_ID);
    }

    private ChannelConfirmHandler handler() {
        return new ChannelConfirmHandler(registry, txSteps);
    }

    @Test
    void 멱등_신규면_mark_후_외부_confirm_하고_reply_OK_를_발행한다() {
        given(registry.resolve(Channel.TOSS)).willReturn(tossStrategy);
        given(txSteps.mark("channelConfirm", EVENT_ID)).willReturn(true);

        handler().onChannelConfirm(command("TOSS"));

        // 순서: mark(외부 전) → confirm(외부) → publishReply(외부 후)
        InOrder inOrder = Mockito.inOrder(txSteps, tossStrategy);
        inOrder.verify(txSteps).mark("channelConfirm", EVENT_ID);
        inOrder.verify(tossStrategy).confirm(EXT_ID);
        ArgumentCaptor<ChannelConfirmReply> replyCaptor = ArgumentCaptor.forClass(ChannelConfirmReply.class);
        inOrder.verify(txSteps).publishReply(replyCaptor.capture());
        ChannelConfirmReply reply = replyCaptor.getValue();
        assertThat(reply.sagaId()).isEqualTo(SAGA_ID);
        assertThat(reply.result()).isEqualTo(ChannelConfirmReply.Result.OK);
    }

    @Test
    void 멱등_중복이면_외부_confirm도_reply도_하지_않는다() {
        given(registry.resolve(Channel.TOSS)).willReturn(tossStrategy);
        given(txSteps.mark("channelConfirm", EVENT_ID)).willReturn(false);

        handler().onChannelConfirm(command("TOSS"));

        verify(tossStrategy, never()).confirm(any());
        verify(txSteps, never()).publishReply(any());
    }

    @Test
    void 외부_confirm_실패면_reply_FAILED_를_발행한다() {
        given(registry.resolve(Channel.TOSS)).willReturn(tossStrategy);
        given(txSteps.mark("channelConfirm", EVENT_ID)).willReturn(true);
        willThrow(new IllegalStateException("seller-cancel FAQ 모순")).given(tossStrategy).confirm(EXT_ID);

        handler().onChannelConfirm(command("TOSS"));

        ArgumentCaptor<ChannelConfirmReply> replyCaptor = ArgumentCaptor.forClass(ChannelConfirmReply.class);
        verify(txSteps).publishReply(replyCaptor.capture());
        ChannelConfirmReply reply = replyCaptor.getValue();
        assertThat(reply.result()).isEqualTo(ChannelConfirmReply.Result.FAILED);
        assertThat(reply.errorCode()).isEqualTo("CHANNEL_CONFIRM_FAILED");
    }

    @Test
    void 지원하지_않는_채널이면_UnsupportedChannelException_이고_mark_하지_않는다() {
        assertThatThrownBy(() -> handler().onChannelConfirm(command("UNKNOWN")))
                .isInstanceOf(UnsupportedChannelException.class)
                .hasMessageContaining("UNKNOWN")
                .hasMessageContaining(SAGA_ID.toString());

        verify(txSteps, never()).mark(any(), any());
    }

    @Test
    void 전략_미등록이면_ConfirmStrategyNotFoundException_이고_mark_하지_않는다() {
        // resolve 가 mark 보다 먼저 — 설정 오류가 멱등 슬롯을 소비하지 않음
        willThrow(new ConfirmStrategyNotFoundException(Channel.TOSS)).given(registry).resolve(Channel.TOSS);

        assertThatThrownBy(() -> handler().onChannelConfirm(command("TOSS")))
                .isInstanceOf(ConfirmStrategyNotFoundException.class);

        verify(txSteps, never()).mark(any(), any());
    }
}
