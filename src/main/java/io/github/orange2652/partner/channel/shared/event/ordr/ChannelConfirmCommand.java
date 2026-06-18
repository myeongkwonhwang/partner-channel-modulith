package io.github.orange2652.partner.channel.shared.event.ordr;

import java.util.Objects;
import java.util.UUID;

/**
 * A1 step3 — channelConfirm command (외부 채널 주문 수락 통보 요청). saga 발행 → adapter 수신.
 *
 * <p>R2 신규 step: validate 통과 후에만 외부에 수락을 통보한다 (보상 케이스 감소). 채널별 {@code ConfirmStrategy}
 * 가 흡수 — 토스=PREPARING_PRODUCT 전이, 네이버=발주확인 API, 쿠팡=상품준비중 전용 endpoint (R5b). 외부 호출은
 * Tx 밖.</p>
 *
 * @param sagaId                 상관 키
 * @param channel                채널 코드 — ConfirmStrategy 디스패치
 * @param externalOrderProductId 외부 전이 대상 식별자
 */
public record ChannelConfirmCommand(
        UUID sagaId,
        String channel,
        String externalOrderProductId
) {
    public ChannelConfirmCommand {
        Objects.requireNonNull(sagaId, "sagaId");
        Objects.requireNonNull(channel, "channel");
        Objects.requireNonNull(externalOrderProductId, "externalOrderProductId");
    }
}
