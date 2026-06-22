package io.github.orange2652.partner.channel.adapter.ordr.application;

import io.github.orange2652.partner.channel.adapter.ordr.domain.ConfirmStrategyNotFoundException;
import io.github.orange2652.partner.channel.shared.domain.Channel;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Component;

/**
 * 채널별 {@link ConfirmStrategy} 디스패치 Registry (R5b D-2) — OCP·매직스트링 금지.
 *
 * <p>Spring 이 주입한 {@code List<ConfirmStrategy>} 를 각 전략의 {@link ConfirmStrategy#channel()} 키로
 * {@code EnumMap} 에 수집한다. 새 채널 전략은 빈으로 추가만 하면 자동 편입된다(분기문 수정 불필요). 같은 채널에
 * 둘 이상 등록되면 즉시 실패(설정 오류 — 조용한 덮어쓰기 방지).</p>
 *
 * <p>구현체는 Phase 2 이연(R5b D-5)이라 현재 주입 목록은 비어 있을 수 있다 — 그 경우 {@link #resolve}는
 * {@link ConfirmStrategyNotFoundException} 으로 fail-fast 한다.</p>
 */
@Component
class ConfirmStrategyRegistry {

    private final Map<Channel, ConfirmStrategy> byChannel = new EnumMap<>(Channel.class);

    ConfirmStrategyRegistry(List<ConfirmStrategy> strategies) {
        for (ConfirmStrategy strategy : strategies) {
            ConfirmStrategy previous = byChannel.put(strategy.channel(), strategy);
            if (previous != null) {
                throw new IllegalStateException(
                        "한 채널에 ConfirmStrategy 중복 등록: channel=" + strategy.channel());
            }
        }
    }

    /**
     * @throws ConfirmStrategyNotFoundException 해당 채널 전략이 없을 때(구현 미배포 신호)
     */
    ConfirmStrategy resolve(Channel channel) {
        ConfirmStrategy strategy = byChannel.get(channel);
        if (strategy == null) {
            throw new ConfirmStrategyNotFoundException(channel);
        }
        return strategy;
    }
}
