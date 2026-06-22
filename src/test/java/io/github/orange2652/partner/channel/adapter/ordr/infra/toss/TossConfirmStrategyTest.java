package io.github.orange2652.partner.channel.adapter.ordr.infra.toss;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import io.github.orange2652.partner.channel.adapter.ordr.domain.TossApiException;
import io.github.orange2652.partner.channel.shared.domain.ExternalOrderStatus;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/**
 * {@link TossConfirmStrategy} 단위 테스트 — client/query mock. 사전 GET 가드 + 전이 + 부분성공 해석(R6).
 */
@ExtendWith(MockitoExtension.class)
class TossConfirmStrategyTest {

    private static final String EXT_ID = "123";

    @Mock
    private TossApiClient client;

    @Mock
    private TossOrderQueryAdapter queryAdapter;

    private TossConfirmStrategy strategy() {
        return new TossConfirmStrategy(client, queryAdapter);
    }

    @Test
    void PAID_면_PREPARING_PRODUCT_로_전이한다() {
        given(queryAdapter.queryStatus(EXT_ID)).willReturn(ExternalOrderStatus.PAID);
        given(client.changeProductStatus(List.of(123L), TossOrderStatus.PREPARING_PRODUCT))
                .willReturn(new TossApiClient.ChangeStatusResult(1, 0, List.of()));

        strategy().confirm(EXT_ID);

        verify(client).changeProductStatus(List.of(123L), TossOrderStatus.PREPARING_PRODUCT);
    }

    @Test
    void 이미_ACCEPTED_면_멱등_NoOp_으로_전이하지_않는다() {
        given(queryAdapter.queryStatus(EXT_ID)).willReturn(ExternalOrderStatus.ACCEPTED);

        strategy().confirm(EXT_ID);

        verify(client, never()).changeProductStatus(any(), any());
    }

    @Test
    void PAID_도_ACCEPTED_도_아니면_confirm_불가_예외() {
        given(queryAdapter.queryStatus(EXT_ID)).willReturn(ExternalOrderStatus.CANCELED);

        assertThatThrownBy(() -> strategy().confirm(EXT_ID))
                .isInstanceOf(TossApiException.class)
                .hasMessageContaining("CANCELED");
        verify(client, never()).changeProductStatus(any(), any());
    }

    @Test
    void 부분성공_실패건이_있으면_예외() {
        given(queryAdapter.queryStatus(EXT_ID)).willReturn(ExternalOrderStatus.PAID);
        given(client.changeProductStatus(List.of(123L), TossOrderStatus.PREPARING_PRODUCT))
                .willReturn(new TossApiClient.ChangeStatusResult(1, 1, List.of("이미 취소됨")));

        assertThatThrownBy(() -> strategy().confirm(EXT_ID))
                .isInstanceOf(TossApiException.class);
    }
}
