package io.github.orange2652.partner.channel.adapter.ordr.infra.toss;

import io.github.orange2652.partner.channel.shared.domain.ExternalOrderStatus;
import java.util.Optional;

/**
 * 토스 주문상품 lifecycle 상태 (R6) — {@code GET /orders/v2} 응답 {@code orderProductStatus} 의 19값. 읽기 전용
 * (쓰기 4값·조회필터 6값과 다른 집합이라 합치지 않는다 — R6 D1). adapter 토스 infra 격리(다른 모듈/채널에 미노출).
 *
 * <p>{@link #toExternal()} 로 채널 무관 추상 {@link ExternalOrderStatus} 에 매핑한다 — saga/scanner 는 추상값만 본다.
 * PAID→PAID(confirm 대상), PREPARING_PRODUCT→ACCEPTED(수락됨), CANCELED_PAYMENT→CANCELED(취소됨), 나머지는 본
 * A1 흐름이 직접 다루지 않으므로 OTHER.</p>
 */
enum TossOrderStatus {

    BEFORE_PAYMENT,
    PAID,
    PREPARING_PRODUCT,
    DELIVERING,
    DELIVERED,
    CONFIRMED_ORDER,
    CLAIM_REQUESTED_CANCEL,
    CANCELED_PAYMENT,
    CLAIM_REJECTED_CANCEL,
    REQUESTED_EXCHANGE,
    ONGOING_EXCHANGE,
    COMPLETED_EXCHANGE,
    CLAIM_REJECTED_EXCHANGE,
    REQUESTED_RETURN,
    ONGOING_RETURN,
    COMPLETED_RETURN,
    CLAIM_REJECTED_RETURN,
    CLAIM_COLLECTING,
    CLAIM_COLLECTED,
    CLAIM_DELIVERING;

    ExternalOrderStatus toExternal() {
        return switch (this) {
            case PAID -> ExternalOrderStatus.PAID;
            case PREPARING_PRODUCT -> ExternalOrderStatus.ACCEPTED;
            case CANCELED_PAYMENT -> ExternalOrderStatus.CANCELED;
            default -> ExternalOrderStatus.OTHER;
        };
    }

    /** 토스 raw 문자열 → enum. 미지원/null 이면 empty(호출자가 OTHER 등으로 방어). */
    static Optional<TossOrderStatus> fromCode(String code) {
        if (code == null) {
            return Optional.empty();
        }
        for (TossOrderStatus s : values()) {
            if (s.name().equals(code)) {
                return Optional.of(s);
            }
        }
        return Optional.empty();
    }
}
