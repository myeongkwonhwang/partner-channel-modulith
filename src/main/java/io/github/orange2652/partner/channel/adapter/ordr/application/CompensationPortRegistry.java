package io.github.orange2652.partner.channel.adapter.ordr.application;

import io.github.orange2652.partner.channel.shared.domain.Channel;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.springframework.stereotype.Component;

/**
 * 채널별 {@link CompensationPort} 디스패치 Registry (R5b D-2) — OCP·매직스트링 금지. {@link ConfirmStrategyRegistry}
 * 와 같은 빈 자동수집 패턴.
 *
 * <p>다만 미등록 시 동작이 다르다 — confirm 은 fail-fast(진행 불가)지만, <b>cancel 은 미등록=외부 cancel 미가용</b>
 * 이라 {@link #find} 가 {@link Optional#empty()} 를 돌려주고 호출자가 {@code MANUAL_REQUIRED} terminal 로 처리한다
 * (R4 {@code PENDING_MANUAL_CANCEL} fallback — 주문을 잃지 않고 운영 개입으로 넘김). 구현체는 Phase 2 이연(R5b D-5)
 * 이라 현재 주입 목록은 비어 있을 수 있다.</p>
 */
@Component
class CompensationPortRegistry {

    private final Map<Channel, CompensationPort> byChannel = new EnumMap<>(Channel.class);

    CompensationPortRegistry(List<CompensationPort> ports) {
        for (CompensationPort port : ports) {
            CompensationPort previous = byChannel.put(port.channel(), port);
            if (previous != null) {
                throw new IllegalStateException(
                        "한 채널에 CompensationPort 중복 등록: channel=" + port.channel());
            }
        }
    }

    /** 해당 채널 보상 Port. 미등록이면 {@link Optional#empty()}(외부 cancel 미가용 → 호출자가 MANUAL_REQUIRED 처리). */
    Optional<CompensationPort> find(Channel channel) {
        return Optional.ofNullable(byChannel.get(channel));
    }
}
