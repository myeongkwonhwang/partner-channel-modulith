package io.github.orange2652.partner.channel.shared.domain;

/**
 * 판매자 취소 시 배송비 귀책 주체 (R4/R6) — 채널 무관 비즈니스 개념.
 *
 * <p>토스 seller-cancel 의 필수 필드 {@code deliveryPenaltyCharger(USER|MERCHANT)} 와 enum 이 일치한다. 다른 채널
 * (네이버·쿠팡)도 환불·배송비 귀책 개념이 있어 shared 에 둔다(R5b 재사용). 보상이 셀러 주도 취소면 {@link #MERCHANT}
 * (R4 D3 — validate 거절·channelConfirm 실패 모두 셀러 주도라 MERCHANT). 구매자 주도 취소는 채널이 자동 처리하므로
 * 본 보상 경로에서는 쓰이지 않는다.</p>
 */
public enum DeliveryPenaltyCharger {

    USER,
    MERCHANT
}
