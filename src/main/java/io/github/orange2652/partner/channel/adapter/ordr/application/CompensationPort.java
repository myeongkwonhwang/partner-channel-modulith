package io.github.orange2652.partner.channel.adapter.ordr.application;

import io.github.orange2652.partner.channel.shared.domain.CancelReason;
import io.github.orange2652.partner.channel.shared.domain.Channel;

/**
 * 채널별 외부 취소 전략 Port (R4 / R5b D-3 / R6 D5) — adapter 책임. step3 보상(channelConfirm cancel)과
 * validate 거절 cancel 의 공통 외부 호출부.
 *
 * <p>채널별 취소 규격이 다르다(토스 seller-cancel / 네이버 cancel·approve / 쿠팡 cancel). 별도 CancelStrategy 를
 * 두지 않고 본 Port 를 R4·R5b 가 함께 재사용한다(R5b D-3). 디스패치는 {@code Map<Channel, CompensationPort>}
 * 빈 자동수집 Registry(R5b D-2). 보상 전 사전 {@code GET}({@link OrderQueryPort})으로 외부 현재 status 를 확인해
 * 이미 취소면 skip(멱등). 외부 호출이므로 호출자 Tx <b>바깥</b>에서 실행한다.</p>
 *
 * <p><b>실패 처리</b>(R4): 외부 cancel 이 불가/영구실패면 saga 를 {@code PENDING_MANUAL_CANCEL} terminal 로
 * 전이하고 운영자에게 알린다(별도 액션 X — 트리거만 확장). 배송 페널티 귀책은 {@link CancelReason#charger}
 * 로 1급 표현한다(R6 D5 — reason 문자열 인코딩에서 타입으로 정본화, R4 보상=MERCHANT). 토스 구현체는 단위 ②
 * (R6 D6)에서 추가한다.</p>
 */
public interface CompensationPort {

    /** 디스패치 키 — Registry 가 본 값으로 채널 라우팅한다. */
    Channel channel();

    /**
     * 외부 채널 주문을 취소한다(seller-cancel). 사전 GET 가드로 이미 취소면 NoOp(멱등).
     *
     * @param externalOrderProductId 취소 대상 식별자
     * @param reason                 취소 사유 — 귀책 주체/요약/상세를 담은 값 객체
     */
    void cancel(String externalOrderProductId, CancelReason reason);
}
