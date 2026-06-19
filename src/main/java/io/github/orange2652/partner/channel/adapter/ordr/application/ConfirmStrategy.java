package io.github.orange2652.partner.channel.adapter.ordr.application;

import io.github.orange2652.partner.channel.shared.domain.Channel;

/**
 * 채널별 주문 수락 통보 전략 Port (R5b D-1, A1 step3 channelConfirm) — adapter 책임.
 *
 * <p>validate(step2) 통과 후에만 외부에 수락을 통보한다(보상 케이스 감소). 채널마다 규격이 다르다
 * (토스=PUT status PREPARING_PRODUCT, 네이버=발주확인 API, 쿠팡=상품준비중 endpoint). 디스패치는 이후
 * {@code Map<Channel, ConfirmStrategy>} 빈 자동수집 Registry(R5b D-2, OCP·매직스트링 금지)로 한다.
 * 외부 호출이므로 호출자 Tx <b>바깥</b>에서 실행한다(협업원칙 — 외부+DB 분리).</p>
 *
 * <p>구현체(채널별)는 미확정 spec 격리를 위해 Phase 2 이연(R5b D-5). 실패 시 saga 가 step3 보상
 * ({@code CompensateChannelConfirmRequested})으로 진입한다. Phase 1 은 Port 골격만 둔다.</p>
 */
public interface ConfirmStrategy {

    /** 디스패치 키 — Registry 가 본 값으로 채널 라우팅한다. */
    Channel channel();

    /**
     * 외부 채널에 주문 수락을 통보한다(예: 토스 PAID→PREPARING_PRODUCT 전이).
     *
     * @param externalOrderProductId 외부 전이 대상 식별자
     */
    void confirm(String externalOrderProductId);
}
