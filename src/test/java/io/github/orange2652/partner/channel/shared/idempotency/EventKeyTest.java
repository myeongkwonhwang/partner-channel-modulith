package io.github.orange2652.partner.channel.shared.idempotency;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.github.orange2652.partner.channel.shared.domain.Channel;
import org.junit.jupiter.api.Test;

/**
 * {@link EventKey} 단위 테스트 — 키 조합/파싱을 한 곳에 모으는 계약 검증.
 *
 * <p>검증: ① {@code of}/{@code channelCodeOf}/{@code externalOrderProductIdOf} 왕복, ② 첫 구분자 기준 분리라
 * 외부 식별자에 구분자가 있어도 보존, ③ 구분자 없는 잘못된 형식은 파싱 거부.</p>
 */
class EventKeyTest {

    @Test
    void of_와_파싱이_왕복한다() {
        String key = EventKey.of(Channel.TOSS, "o-1");

        assertThat(key).isEqualTo("TOSS:o-1");
        assertThat(EventKey.channelCodeOf(key)).isEqualTo("TOSS");
        assertThat(EventKey.externalOrderProductIdOf(key)).isEqualTo("o-1");
    }

    @Test
    void 외부_식별자에_구분자가_있어도_첫_구분자_기준으로_분리한다() {
        String key = EventKey.of("NAVER", "order:2025:7");

        assertThat(EventKey.channelCodeOf(key)).isEqualTo("NAVER");
        assertThat(EventKey.externalOrderProductIdOf(key)).isEqualTo("order:2025:7");
    }

    @Test
    void 구분자가_없으면_파싱을_거부한다() {
        assertThatThrownBy(() -> EventKey.externalOrderProductIdOf("no-separator"))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
