package io.github.orange2652.partner.channel.adapter.ordr.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.github.orange2652.partner.channel.adapter.ordr.domain.ConfirmStrategyNotFoundException;
import io.github.orange2652.partner.channel.shared.domain.Channel;
import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * {@link ConfirmStrategyRegistry} 단위 테스트 — 빈 자동수집 디스패치(R5b D-2).
 *
 * <p>검증: ① 등록된 채널 resolve 성공, ② 미등록 채널 → {@link ConfirmStrategyNotFoundException}, ③ 빈 목록(구현
 * Phase 2 이연 상태) → 어떤 채널도 미등록, ④ 한 채널 중복 등록 → 즉시 실패(조용한 덮어쓰기 방지).</p>
 */
class ConfirmStrategyRegistryTest {

    /** 테스트용 채널 고정 전략 — confirm 은 no-op. */
    private static ConfirmStrategy strategyFor(Channel channel) {
        return new ConfirmStrategy() {
            @Override
            public Channel channel() {
                return channel;
            }

            @Override
            public void confirm(String externalOrderProductId) {
                // no-op
            }
        };
    }

    @Test
    void 등록된_채널은_해당_전략을_resolve_한다() {
        ConfirmStrategy toss = strategyFor(Channel.TOSS);
        ConfirmStrategyRegistry registry = new ConfirmStrategyRegistry(List.of(toss));

        assertThat(registry.resolve(Channel.TOSS)).isSameAs(toss);
    }

    @Test
    void 미등록_채널은_ConfirmStrategyNotFoundException() {
        ConfirmStrategyRegistry registry = new ConfirmStrategyRegistry(List.of(strategyFor(Channel.TOSS)));

        assertThatThrownBy(() -> registry.resolve(Channel.NAVER))
                .isInstanceOf(ConfirmStrategyNotFoundException.class)
                .hasMessageContaining("NAVER");
    }

    @Test
    void 빈_목록이면_모든_채널이_미등록이다() {
        ConfirmStrategyRegistry registry = new ConfirmStrategyRegistry(List.of());

        assertThatThrownBy(() -> registry.resolve(Channel.TOSS))
                .isInstanceOf(ConfirmStrategyNotFoundException.class);
    }

    @Test
    void 한_채널에_중복_등록되면_생성_시점에_실패한다() {
        List<ConfirmStrategy> dup = List.of(strategyFor(Channel.TOSS), strategyFor(Channel.TOSS));

        assertThatThrownBy(() -> new ConfirmStrategyRegistry(dup))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("TOSS");
    }
}
