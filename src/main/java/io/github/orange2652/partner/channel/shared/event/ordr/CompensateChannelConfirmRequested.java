package io.github.orange2652.partner.channel.shared.event.ordr;

import java.util.Objects;
import java.util.UUID;

/**
 * A1 보상 — channelConfirm(step3) 보상 요청 (R4). saga 발행 → adapter 수신.
 *
 * <p>외부 채널 취소 호출 (토스 seller-cancel / 네이버 cancel·approve / 쿠팡 cancel — R5b CompensationPort).
 * 보상 전 사전 {@code GET} 으로 외부 현재 status 를 확인해 이미 취소면 skip(멱등). 외부 호출은 Tx 밖.</p>
 *
 * @param sagaId                 상관 키
 * @param channel                채널 코드 — CompensationPort 디스패치
 * @param externalOrderProductId 취소 대상 식별자
 * @param reason                 보상 사유(식별/운영용)
 */
public record CompensateChannelConfirmRequested(
        UUID sagaId,
        String channel,
        String externalOrderProductId,
        String reason
) {
    public CompensateChannelConfirmRequested {
        Objects.requireNonNull(sagaId, "sagaId");
        Objects.requireNonNull(channel, "channel");
        Objects.requireNonNull(externalOrderProductId, "externalOrderProductId");
    }
}
