package io.github.orange2652.partner.channel.adapter.ordr.domain;

import io.github.orange2652.partner.channel.shared.domain.Channel;

/**
 * 채널에 대응하는 {@code ConfirmStrategy} 빈이 등록되지 않음 — step3 디스패치 실패.
 *
 * <p>채널 구현체는 미확정 spec 격리를 위해 Phase 2 이연(R5b D-5)이라, 구현체가 아직 없는 채널로 step3 가 진입하면
 * 본 예외가 fail-fast 한다(설정/배포 누락 신호). 구현체가 붙으면 자연히 해소된다 — 무음 skip 보다 명시적 예외가
 * 운영 가시성에 낫다.</p>
 */
public final class ConfirmStrategyNotFoundException extends ChannelException {

    private static final String CODE = "ADAPTER_CONFIRM_STRATEGY_NOT_FOUND";

    public ConfirmStrategyNotFoundException(Channel channel) {
        super(CODE, "등록된 ConfirmStrategy 없음: channel=" + channel);
    }
}
