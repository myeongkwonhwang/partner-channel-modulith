package io.github.orange2652.partner.channel.shared.event.ordr;

import io.github.orange2652.partner.channel.shared.domain.DeliveryPenaltyCharger;
import java.util.Objects;
import java.util.UUID;

/**
 * A1 보상 — channelConfirm(step3) 보상 요청 (R4/R6). saga 발행 → adapter 수신.
 *
 * <p>외부 채널 취소 호출 (토스 seller-cancel / 네이버 cancel·approve / 쿠팡 cancel — R5b CompensationPort).
 * 보상 전 사전 {@code GET} 으로 외부 현재 status 를 확인해 이미 취소면 skip(멱등). 외부 호출은 Tx 밖.</p>
 *
 * <p>{@code deliveryPenaltyCharger}(귀책)는 saga 가 결정하는 비즈니스 값으로 event 에 실어 adapter 로 운반한다
 * (R6 D5 — 보상은 셀러 주도라 {@code MERCHANT}). adapter 는 이 값으로 {@code CancelReason} 을 조립해 실행만 한다.</p>
 *
 * @param sagaId                 상관 키
 * @param channel                채널 코드 — CompensationPort 디스패치
 * @param externalOrderProductId 취소 대상 식별자
 * @param reason                 보상 사유(식별/운영용)
 * @param deliveryPenaltyCharger 배송비 귀책 주체(R4 보상=MERCHANT)
 */
public record CompensateChannelConfirmRequested(
        UUID sagaId,
        String channel,
        String externalOrderProductId,
        String reason,
        DeliveryPenaltyCharger deliveryPenaltyCharger
) {
    public CompensateChannelConfirmRequested {
        Objects.requireNonNull(sagaId, "sagaId");
        Objects.requireNonNull(channel, "channel");
        Objects.requireNonNull(externalOrderProductId, "externalOrderProductId");
        Objects.requireNonNull(deliveryPenaltyCharger, "deliveryPenaltyCharger");
    }
}
