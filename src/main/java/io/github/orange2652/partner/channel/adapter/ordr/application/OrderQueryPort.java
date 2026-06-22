package io.github.orange2652.partner.channel.adapter.ordr.application;

import io.github.orange2652.partner.channel.shared.domain.Channel;
import io.github.orange2652.partner.channel.shared.domain.ExternalOrderStatus;

/**
 * 외부 주문 상태 조회 Port (R6 D2) — adapter 책임. 사전 GET 가드(보상/step3 전 외부 현재 status 확인)와 R5a stuck
 * scanner 의 외부 진실 판정이 공통 소비한다. 읽기-쓰기 관심사 분리로 {@code ConfirmStrategy}/{@code CompensationPort}
 * (쓰기)와 별도 Port 로 둔다(헥사 정석).
 *
 * <p>채널 raw 상태를 채널 무관 추상 {@link ExternalOrderStatus} 로 매핑해 반환한다(매핑은 채널 구현체 책임 —
 * saga/scanner 가 채널 어휘에 결합하지 않게). 외부 호출이므로 호출자 Tx <b>바깥</b>에서 실행한다.</p>
 *
 * <p><b>토스 제약(R6)</b>: 토스는 단건 조회 전용 GET endpoint 가 없어 구현체는 {@code GET /orders/v2}(좁은 기간 +
 * status 필터 + cursor)로 1건을 찾아 status 를 추출한다. R5a scanner 가 대량 stuck 을 건당 조회하면 읽기 RL(50/s)
 * 부담이라, 일괄 조회 최적화는 Phase 4 이연(처리량 드라이버 발생 시). 구현체는 단위 ②(R6 D6) 에서 추가한다.</p>
 */
public interface OrderQueryPort {

    /** 디스패치 키 — Registry(Phase 4 scanner 도입 시) 가 본 값으로 채널 라우팅한다. */
    Channel channel();

    /**
     * 외부 채널의 현재 주문 상태를 조회해 추상 상태로 반환한다.
     *
     * @param externalOrderProductId 조회 대상 외부 식별자
     * @return 채널 무관 추상 상태
     */
    ExternalOrderStatus queryStatus(String externalOrderProductId);
}
