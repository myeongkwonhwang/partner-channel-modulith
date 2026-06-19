package io.github.orange2652.partner.channel.adapter.ordr.application;

import io.github.orange2652.partner.channel.shared.domain.Channel;

/**
 * 채널별 외부 취소 전략 Port (R4 / R5b D-3) — adapter 책임. step3 보상(channelConfirm cancel)과
 * validate 거절 cancel 의 공통 외부 호출부.
 *
 * <p>채널별 취소 규격이 다르다(토스 seller-cancel / 네이버 cancel·approve / 쿠팡 cancel). 별도 CancelStrategy 를
 * 두지 않고 본 Port 를 R4·R5b 가 함께 재사용한다(R5b D-3). 디스패치는 {@code Map<Channel, CompensationPort>}
 * 빈 자동수집 Registry(R5b D-2). 보상 전 사전 {@code GET} 으로 외부 현재 status 를 확인해 이미 취소면
 * skip(멱등). 외부 호출이므로 호출자 Tx <b>바깥</b>에서 실행한다.</p>
 *
 * <p><b>실패 처리</b>(R4): 외부 cancel 이 불가/영구실패면 saga 를 {@code PENDING_MANUAL_CANCEL} terminal 로
 * 전이하고 운영자에게 알린다(별도 액션 X — 트리거만 확장). validate 거절 보상은 배송 페널티 귀책이 셀러
 * ({@code deliveryPenaltyCharger=MERCHANT})임을 사유에 명시한다. 구현체는 sandbox 검증 후 교체(Phase 2 이연).</p>
 */
public interface CompensationPort {

    /** 디스패치 키 — Registry 가 본 값으로 채널 라우팅한다. */
    Channel channel();

    /**
     * 외부 채널 주문을 취소한다(seller-cancel). 사전 GET 가드로 이미 취소면 NoOp(멱등).
     *
     * @param externalOrderProductId 취소 대상 식별자
     * @param reason                 보상 사유(식별/운영용 — 메시지 본문에 명시)
     */
    void cancel(String externalOrderProductId, String reason);
}
