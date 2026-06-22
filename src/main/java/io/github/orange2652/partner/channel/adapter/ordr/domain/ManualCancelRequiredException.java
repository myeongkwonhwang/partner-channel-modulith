package io.github.orange2652.partner.channel.adapter.ordr.domain;

/**
 * 외부 seller-cancel 이 영구 불가 — 운영자 수동 취소가 필요함을 알리는 신호 (R4).
 *
 * <p>{@code CompensationPort.cancel} 구현이 채널 규정상 자동 취소가 불가하다고 판단할 때 던진다(예: 토스 seller-cancel
 * FAQ 모순 흡수). adapter 보상 핸들러는 이를 {@code ChannelConfirmCompensated.MANUAL_REQUIRED} 로 매핑해 saga 가
 * {@code PENDING_MANUAL_CANCEL} terminal 로 가게 한다. 그 외 transient 실패는 {@code FAILED}(재시도)로 구분된다.</p>
 */
public final class ManualCancelRequiredException extends ChannelException {

    private static final String CODE = "ADAPTER_MANUAL_CANCEL_REQUIRED";

    public ManualCancelRequiredException(String externalOrderProductId, String reason) {
        super(CODE, "외부 cancel 자동 수행 불가 — 수동 개입 필요: externalOrderProductId="
                + externalOrderProductId + " reason=" + reason);
    }
}
