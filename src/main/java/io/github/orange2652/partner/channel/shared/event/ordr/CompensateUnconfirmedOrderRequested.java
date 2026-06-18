package io.github.orange2652.partner.channel.shared.event.ordr;

import java.util.Objects;
import java.util.UUID;

/**
 * A1 보상 — unconfirmedOrder(step1) 보상 요청 (R4). saga 발행 → adapter 수신.
 *
 * <p>staging row 를 CANCELED 로 UPDATE (DELETE 아님). 이미 CANCELED 면 NoOp(멱등).</p>
 *
 * @param sagaId    상관 키
 * @param channel   채널 코드
 * @param stagingId 취소 대상 staging row PK
 * @param reason    보상 사유(식별/운영용)
 */
public record CompensateUnconfirmedOrderRequested(
        UUID sagaId,
        String channel,
        Long stagingId,
        String reason
) {
    public CompensateUnconfirmedOrderRequested {
        Objects.requireNonNull(sagaId, "sagaId");
        Objects.requireNonNull(channel, "channel");
        Objects.requireNonNull(stagingId, "stagingId");
    }
}
