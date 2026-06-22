package io.github.orange2652.partner.channel.adapter.ordr.infra.toss;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;

import io.github.orange2652.partner.channel.shared.domain.Channel;
import io.github.orange2652.partner.channel.shared.domain.ExternalOrderStatus;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/**
 * {@link TossOrderQueryAdapter} 단위 테스트 — {@link TossApiClient} mock. raw 상태 매핑 + 미발견 방어(R6).
 */
@ExtendWith(MockitoExtension.class)
class TossOrderQueryAdapterTest {

    private static final String EXT_ID = "123";

    @Mock
    private TossApiClient client;

    private TossOrderQueryAdapter adapter() {
        return new TossOrderQueryAdapter(client);
    }

    @Test
    void channel_은_TOSS() {
        assertThat(adapter().channel()).isEqualTo(Channel.TOSS);
    }

    @Test
    void raw_상태를_추상_상태로_매핑한다() {
        given(client.findOrderProductStatus(123L)).willReturn(Optional.of(TossOrderStatus.PREPARING_PRODUCT));

        assertThat(adapter().queryStatus(EXT_ID)).isEqualTo(ExternalOrderStatus.ACCEPTED);
    }

    @Test
    void 기간내_미발견이면_OTHER_로_방어한다() {
        given(client.findOrderProductStatus(123L)).willReturn(Optional.empty());

        assertThat(adapter().queryStatus(EXT_ID)).isEqualTo(ExternalOrderStatus.OTHER);
    }
}
