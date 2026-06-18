package io.github.orange2652.partner.channel.shared.event.ordr;

import java.util.Objects;
import java.util.UUID;

/**
 * A1 step1 — unconfirmedOrder command (외부 raw → staging 미확정 적재 요청).
 *
 * <p>saga 발행 → adapter 수신. R2 분기: step1 은 staging INSERT 만 한다 (외부 PREPARING_PRODUCT 전이는
 * step3 channelConfirm 으로 분리). channel raw 의 정규화(externalOrderProductId 추출 등)는 adapter 책임.</p>
 *
 * @param sagaId  상관 키 (saga 인스턴스)
 * @param channel 채널 코드
 * @param raw     채널 응답 원본(JSON)
 */
public record UnconfirmedOrderCommand(
        UUID sagaId,
        String channel,
        String raw
) {
    public UnconfirmedOrderCommand {
        Objects.requireNonNull(sagaId, "sagaId");
        Objects.requireNonNull(channel, "channel");
        Objects.requireNonNull(raw, "raw");
    }
}
