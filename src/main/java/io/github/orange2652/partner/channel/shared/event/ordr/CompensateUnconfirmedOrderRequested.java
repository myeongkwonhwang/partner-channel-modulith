package io.github.orange2652.partner.channel.shared.event.ordr;

import java.util.Objects;
import java.util.UUID;

/**
 * A1 보상 — unconfirmedOrder(step1) 보상 요청 (R4). saga 발행 → adapter 수신.
 *
 * <p>staging row 를 CANCELED 로 UPDATE (DELETE 아님). 이미 CANCELED 면 NoOp(멱등).</p>
 *
 * <p>대상 row 는 {@code (channel, externalOrderProductId)} 자연키로 찾는다(staging_order UNIQUE). saga 는 이 키를
 * {@code correlationKey} 로 항상 보유하므로, 채번된 surrogate stagingId 를 saga_state 에 따로 영속할 필요가 없다
 * (스캐폴드 초안의 {@code stagingId} 운반에서 자연키로 정렬 — 와이어링 시점 결정).</p>
 *
 * @param sagaId                 상관 키
 * @param channel                채널 코드
 * @param externalOrderProductId 취소 대상 staging 자연키
 * @param reason                 보상 사유(식별/운영용)
 */
public record CompensateUnconfirmedOrderRequested(
        UUID sagaId,
        String channel,
        String externalOrderProductId,
        String reason
) {
    public CompensateUnconfirmedOrderRequested {
        Objects.requireNonNull(sagaId, "sagaId");
        Objects.requireNonNull(channel, "channel");
        Objects.requireNonNull(externalOrderProductId, "externalOrderProductId");
    }
}
