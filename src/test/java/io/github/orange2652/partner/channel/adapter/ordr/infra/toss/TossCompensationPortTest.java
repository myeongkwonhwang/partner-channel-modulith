package io.github.orange2652.partner.channel.adapter.ordr.infra.toss;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.willThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import io.github.orange2652.partner.channel.adapter.ordr.domain.ManualCancelRequiredException;
import io.github.orange2652.partner.channel.adapter.ordr.domain.TossApiException;
import io.github.orange2652.partner.channel.shared.domain.CancelReason;
import io.github.orange2652.partner.channel.shared.domain.DeliveryPenaltyCharger;
import io.github.orange2652.partner.channel.shared.domain.ExternalOrderStatus;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/**
 * {@link TossCompensationPort} 단위 테스트 — client/query mock. 사전 GET 가드 + 귀책 전달 + 실패 분류(R6).
 */
@ExtendWith(MockitoExtension.class)
class TossCompensationPortTest {

    private static final String EXT_ID = "123";
    private static final CancelReason REASON =
            CancelReason.of(DeliveryPenaltyCharger.MERCHANT, "CHANNEL_CONFIRM_FAILED:x");

    @Mock
    private TossApiClient client;

    @Mock
    private TossOrderQueryAdapter queryAdapter;

    private TossCompensationPort port() {
        return new TossCompensationPort(client, queryAdapter);
    }

    @Test
    void 미취소면_seller_cancel_을_귀책과_함께_호출한다() {
        given(queryAdapter.queryStatus(EXT_ID)).willReturn(ExternalOrderStatus.PAID);

        port().cancel(EXT_ID, REASON);

        ArgumentCaptor<TossApiClient.SellerCancelBody> captor =
                ArgumentCaptor.forClass(TossApiClient.SellerCancelBody.class);
        verify(client).sellerCancel(eq(123L), captor.capture());
        assertThat(captor.getValue().deliveryPenaltyCharger()).isEqualTo("MERCHANT");
        assertThat(captor.getValue().reason()).isEqualTo("CHANNEL_CONFIRM_FAILED:x");
    }

    @Test
    void reason_이_토스_한도_초과면_절단해_전달한다() {
        given(queryAdapter.queryStatus(EXT_ID)).willReturn(ExternalOrderStatus.PAID);
        String longReason = "x".repeat(150);
        CancelReason over = new CancelReason(DeliveryPenaltyCharger.MERCHANT, longReason, "d".repeat(300));

        port().cancel(EXT_ID, over);

        ArgumentCaptor<TossApiClient.SellerCancelBody> captor =
                ArgumentCaptor.forClass(TossApiClient.SellerCancelBody.class);
        verify(client).sellerCancel(eq(123L), captor.capture());
        assertThat(captor.getValue().reason()).hasSize(100);
        assertThat(captor.getValue().detailReason()).hasSize(250);
    }

    @Test
    void 이미_CANCELED_면_멱등_NoOp_으로_호출하지_않는다() {
        given(queryAdapter.queryStatus(EXT_ID)).willReturn(ExternalOrderStatus.CANCELED);

        port().cancel(EXT_ID, REASON);

        verify(client, never()).sellerCancel(anyLong(), any());
    }

    @Test
    void transient_실패는_그대로_전파한다_재시도대상() {
        given(queryAdapter.queryStatus(EXT_ID)).willReturn(ExternalOrderStatus.PAID);
        willThrow(new TossApiException("TOO_MANY_REQUEST", true)).given(client).sellerCancel(anyLong(), any());

        assertThatThrownBy(() -> port().cancel(EXT_ID, REASON))
                .isInstanceOf(TossApiException.class);
    }

    @Test
    void 비_transient_실패는_ManualCancelRequired_로_변환한다() {
        given(queryAdapter.queryStatus(EXT_ID)).willReturn(ExternalOrderStatus.PAID);
        willThrow(new TossApiException("INVALID_REQUEST", false)).given(client).sellerCancel(anyLong(), any());

        assertThatThrownBy(() -> port().cancel(EXT_ID, REASON))
                .isInstanceOf(ManualCancelRequiredException.class);
    }
}
