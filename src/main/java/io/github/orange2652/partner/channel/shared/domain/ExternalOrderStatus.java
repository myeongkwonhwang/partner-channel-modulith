package io.github.orange2652.partner.channel.shared.domain;

/**
 * 채널 무관 추상 외부 주문 상태 (R6) — 사전 GET 가드/stuck 복구가 채널 raw 상태에 의존하지 않도록 한 최소 집합.
 *
 * <p>각 채널 어댑터가 자기 raw lifecycle(예: 토스 19값 {@code orderProductStatus})을 본 추상값으로 매핑한다
 * (매핑은 채널 infra 책임 — shared 는 raw 어휘를 모른다). saga/scanner 는 본 추상값만 보고 advance/보상을 판정해
 * 채널 어휘 결합을 피한다(모듈 경계 보호).</p>
 *
 * <ul>
 *   <li>{@link #PAID} — 결제완료, 아직 판매자 수락(step3) 전. confirm 대상.</li>
 *   <li>{@link #ACCEPTED} — 판매자 수락됨(상품준비중 이상, cancel 전). confirm 사후/멱등 판정 기준.</li>
 *   <li>{@link #CANCELED} — 취소/환불됨. 보상 사전 가드의 NoOp 판정 기준.</li>
 *   <li>{@link #OTHER} — 그 외(배송/클레임 등). 본 흐름이 직접 다루지 않는 상태.</li>
 * </ul>
 */
public enum ExternalOrderStatus {

    PAID,
    ACCEPTED,
    CANCELED,
    OTHER
}
