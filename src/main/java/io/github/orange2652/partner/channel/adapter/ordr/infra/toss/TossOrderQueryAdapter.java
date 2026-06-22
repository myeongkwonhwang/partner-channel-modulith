package io.github.orange2652.partner.channel.adapter.ordr.infra.toss;

import io.github.orange2652.partner.channel.adapter.ordr.application.OrderQueryPort;
import io.github.orange2652.partner.channel.shared.domain.Channel;
import io.github.orange2652.partner.channel.shared.domain.ExternalOrderStatus;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * 토스 {@link OrderQueryPort} 구현 (R6 D6) — {@code GET /orders/v2} 로 단건 상태를 조회해 추상
 * {@link ExternalOrderStatus} 로 매핑한다. 사전 GET 가드/scanner 가 채널 무관 추상값만 보게 한다.
 *
 * <p>기간 내 주문을 못 찾으면 {@link ExternalOrderStatus#OTHER} 로 방어 반환한다(존재하지 않거나 lookback 밖 —
 * 가드가 "PAID/CANCELED 아님"으로 보수적으로 처리). raw→추상 매핑은 {@link TossOrderStatus#toExternal()}.</p>
 */
@Component
@RequiredArgsConstructor
class TossOrderQueryAdapter implements OrderQueryPort {

    private final TossApiClient client;

    @Override
    public Channel channel() {
        return Channel.TOSS;
    }

    @Override
    public ExternalOrderStatus queryStatus(String externalOrderProductId) {
        long id = TossApiClient.parseOrderProductId(externalOrderProductId);
        return client.findOrderProductStatus(id)
                .map(TossOrderStatus::toExternal)
                .orElse(ExternalOrderStatus.OTHER);
    }
}
