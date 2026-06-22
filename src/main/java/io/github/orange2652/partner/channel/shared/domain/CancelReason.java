package io.github.orange2652.partner.channel.shared.domain;

import java.util.Objects;

/**
 * 판매자 취소 사유 값 객체 (R6) — {@code CompensationPort.cancel} 인자. 토스 seller-cancel body 의
 * {@code deliveryPenaltyCharger}(필수) / {@code reason}(100자) / {@code detailReason}(250자, 선택) 을 타입으로 표현한다
 * (매직스트링 인코딩 제거 — Effective Java Item 2 정신).
 *
 * <p>{@code charger} 는 귀책 주체로 필수(R4 보상=MERCHANT). {@code reason} 은 식별/운영용 요약. {@code detailReason} 은
 * 선택 상세로 nullable — 현재 보상 흐름은 reason 만 채우고 detailReason 은 미사용(null). 길이 초과는 채널 구현체가
 * 채널 규격(100/250자)에 맞게 절단한다(shared 는 채널 한도를 모른다).</p>
 *
 * @param charger      배송비 귀책 주체 (필수)
 * @param reason       취소 사유 요약 (필수)
 * @param detailReason 취소 상세 사유 (nullable)
 */
public record CancelReason(
        DeliveryPenaltyCharger charger,
        String reason,
        String detailReason
) {
    public CancelReason {
        Objects.requireNonNull(charger, "charger");
        Objects.requireNonNull(reason, "reason");
    }

    /** detailReason 없는 사유. */
    public static CancelReason of(DeliveryPenaltyCharger charger, String reason) {
        return new CancelReason(charger, reason, null);
    }
}
