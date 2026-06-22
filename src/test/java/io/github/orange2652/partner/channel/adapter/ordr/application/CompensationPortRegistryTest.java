package io.github.orange2652.partner.channel.adapter.ordr.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.github.orange2652.partner.channel.shared.domain.CancelReason;
import io.github.orange2652.partner.channel.shared.domain.Channel;
import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * {@link CompensationPortRegistry} 단위 테스트 — 빈 자동수집 디스패치(R5b D-2).
 *
 * <p>검증: ① 등록된 채널 find 성공, ② 미등록 채널 → {@code empty}(외부 cancel 미가용 → 호출자가 MANUAL_REQUIRED),
 * ③ 빈 목록 → 모든 채널 empty, ④ 한 채널 중복 등록 → 즉시 실패.</p>
 */
class CompensationPortRegistryTest {

    private static CompensationPort portFor(Channel channel) {
        return new CompensationPort() {
            @Override
            public Channel channel() {
                return channel;
            }

            @Override
            public void cancel(String externalOrderProductId, CancelReason reason) {
                // no-op
            }
        };
    }

    @Test
    void 등록된_채널은_해당_Port_를_find_한다() {
        CompensationPort toss = portFor(Channel.TOSS);
        CompensationPortRegistry registry = new CompensationPortRegistry(List.of(toss));

        assertThat(registry.find(Channel.TOSS)).containsSame(toss);
    }

    @Test
    void 미등록_채널은_empty_이다() {
        CompensationPortRegistry registry = new CompensationPortRegistry(List.of(portFor(Channel.TOSS)));

        assertThat(registry.find(Channel.NAVER)).isEmpty();
    }

    @Test
    void 빈_목록이면_모든_채널이_empty_이다() {
        CompensationPortRegistry registry = new CompensationPortRegistry(List.of());

        assertThat(registry.find(Channel.TOSS)).isEmpty();
    }

    @Test
    void 한_채널에_중복_등록되면_생성_시점에_실패한다() {
        List<CompensationPort> dup = List.of(portFor(Channel.TOSS), portFor(Channel.TOSS));

        assertThatThrownBy(() -> new CompensationPortRegistry(dup))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("TOSS");
    }
}
