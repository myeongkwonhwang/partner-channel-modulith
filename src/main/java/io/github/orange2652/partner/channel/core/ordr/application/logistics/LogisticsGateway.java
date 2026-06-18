package io.github.orange2652.partner.channel.core.ordr.application.logistics;

import java.util.Optional;
import java.util.UUID;

/**
 * 자사 물류 시스템 의뢰 Gateway (Port) — A1 step4(confirmedOrder, Pivot) 의 외부 호출부.
 *
 * <p><b>왜 Gateway 인가</b>: 외부 판매 채널의 HTTP Client 와 달리 자사 물류는 DB-to-DB / interface table 등
 * 프로토콜 무관 전달이 흔하므로 Clean Architecture 의 Gateway 명명. 자사 시스템이라 core 책임(외부 채널 아님).</p>
 *
 * <p><b>멱등성</b>: 같은 {@code idempotencyKey}(= sagaId)로 두 번 dispatch 되어도 같은 shipmentId 반환.
 * {@link #getShipment} 는 Pivot 부분 실패(외부 OK + 내부 INSERT 실패) reconciliation 에서 외부 상태 확인용.</p>
 */
public interface LogisticsGateway {

    /**
     * 자사 물류 의뢰 — 멱등 dispatch.
     *
     * @param idempotencyKey 멱등 키 (= sagaId)
     * @param request        dispatch 요청 (channel, externalOrderProductId)
     * @return shipmentId 포함 응답
     */
    ShipmentResponse dispatch(UUID idempotencyKey, ShipmentRequest request);

    /**
     * 멱등 조회 (Pivot reconciliation) — dispatch 성공 이력이 있으면 {@link Shipment}, 없으면 empty.
     */
    Optional<Shipment> getShipment(UUID idempotencyKey);
}
