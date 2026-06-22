package io.github.orange2652.partner.channel.shared.event.ordr;

import java.util.Objects;
import java.util.UUID;

/**
 * A1 step1 — unconfirmedOrder command (외부 raw → staging 미확정 적재 요청).
 *
 * <p>saga 발행 → adapter 수신. R2 분기: step1 은 staging INSERT 만 한다 (외부 PREPARING_PRODUCT 전이는
 * step3 channelConfirm 으로 분리).</p>
 *
 * <p><b>{@code externalOrderProductId} 운반 (2026-06-19 Phase 2)</b>: staging 의 UNIQUE·멱등 키에 필요한 식별자는
 * polling 단계에서 이미 추출돼 {@code OrderReceivedEvent} 에 담겨 있으므로 command 로 실어 전달한다. adapter 가 raw 를
 * 재파싱하지 않는다 — 채널별 raw 스키마(네이버/쿠팡 필드명 등)가 미확정이라 재추출은 "추정 금지" 위반. adapter 는 raw →
 * staging 의 다른 필드 매핑(정규화)은 계속 소유한다.</p>
 *
 * @param sagaId                 상관 키 (saga 인스턴스)
 * @param channel                채널 코드
 * @param externalOrderProductId 외부 주문상품 식별자 (staging UNIQUE·멱등 키)
 * @param raw                    채널 응답 원본(JSON)
 */
public record UnconfirmedOrderCommand(
        UUID sagaId,
        String channel,
        String externalOrderProductId,
        String raw
) {
    public UnconfirmedOrderCommand {
        Objects.requireNonNull(sagaId, "sagaId");
        Objects.requireNonNull(channel, "channel");
        Objects.requireNonNull(externalOrderProductId, "externalOrderProductId");
        Objects.requireNonNull(raw, "raw");
    }
}
