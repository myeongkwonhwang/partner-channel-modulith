package io.github.orange2652.partner.channel.core.ordr.application.logistics;

import java.util.Objects;
import java.util.UUID;

/**
 * 자사 물류 의뢰 결과 — {@link LogisticsGateway#getShipment} 반환 타입(Pivot reconciliation).
 *
 * @param shipmentId     자사 물류가 부여한 의뢰 식별자
 * @param idempotencyKey 호출자 멱등 키 (= sagaId)
 */
public record Shipment(
        String shipmentId,
        UUID idempotencyKey
) {
    public Shipment {
        Objects.requireNonNull(shipmentId, "shipmentId");
        Objects.requireNonNull(idempotencyKey, "idempotencyKey");
    }
}
