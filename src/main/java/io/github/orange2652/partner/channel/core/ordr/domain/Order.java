package io.github.orange2652.partner.channel.core.ordr.domain;

import java.time.LocalDateTime;
import java.util.Objects;
import java.util.UUID;

/**
 * 내부 확정 주문 — A1 saga step4(confirmedOrder, Pivot) 통과 시 INSERT.
 *
 * <p>{@code (channel, externalOrderProductId)} UNIQUE — 중복 확정 차단. Pivot 통과 후 saga 자동 보상 불가,
 * 취소는 별도 흐름(B2 — 후속). MSA 와 차이: in-VM 상관 키 {@code sagaId} 를 보유한다(MSA 는 Kafka 헤더).</p>
 *
 * @param id                     DB PK (신규는 null)
 * @param sagaId                 상관 키 (saga 인스턴스)
 * @param channel                채널 코드
 * @param externalOrderProductId 외부 주문상품 식별자 — UNIQUE
 * @param shipmentId             자사 물류 의뢰 식별자
 * @param status                 내부 상태 (Pivot 직후 {@code "CREATED"})
 * @param createdAt              INSERT 시각
 * @param updatedAt              마지막 갱신 시각
 */
public record Order(
        Long id,
        UUID sagaId,
        String channel,
        String externalOrderProductId,
        String shipmentId,
        String status,
        LocalDateTime createdAt,
        LocalDateTime updatedAt
) {
    private static final String STATUS_CREATED = "CREATED";

    public Order {
        Objects.requireNonNull(sagaId, "sagaId");
        Objects.requireNonNull(channel, "channel");
        Objects.requireNonNull(externalOrderProductId, "externalOrderProductId");
        Objects.requireNonNull(shipmentId, "shipmentId");
        Objects.requireNonNull(status, "status");
    }

    /** Pivot 통과 직후의 새 주문 — {@code id=null}, {@code status="CREATED"}, 시각은 now. */
    public static Order newRecord(UUID sagaId, String channel, String externalOrderProductId, String shipmentId) {
        LocalDateTime now = LocalDateTime.now();
        return new Order(null, sagaId, channel, externalOrderProductId, shipmentId, STATUS_CREATED, now, now);
    }
}
