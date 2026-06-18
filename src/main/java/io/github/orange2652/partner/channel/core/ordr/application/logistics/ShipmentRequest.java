package io.github.orange2652.partner.channel.core.ordr.application.logistics;

import java.util.Objects;

/**
 * 자사 물류 의뢰 요청.
 *
 * @param channel                채널 코드
 * @param externalOrderProductId 외부 주문상품 식별자
 */
public record ShipmentRequest(
        String channel,
        String externalOrderProductId
) {
    public ShipmentRequest {
        Objects.requireNonNull(channel, "channel");
        Objects.requireNonNull(externalOrderProductId, "externalOrderProductId");
    }
}
