package io.github.orange2652.partner.channel.batch.ordr.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.github.orange2652.partner.channel.shared.domain.Channel;
import java.time.Instant;
import org.junit.jupiter.api.Test;

/**
 * {@link PolledOrder} 결과 record 순수 단위 테스트 (프레임워크 0).
 *
 * <p>검증 불변식: ① 필수 필드 {@code requireNonNull} 가드(네 필드 전부), ② 필드 보존. 시각 경계는
 * {@link Instant} 이므로 외부 채널값({@code OrderReceivedEvent.orderedAt}) 정렬을 함께 본다.</p>
 *
 * <p>{@link PollingStrategy} 는 구현체가 없어(Phase 2 이연) mock 흐름 테스트 대상이 아니다 — 본 라운드 제외.</p>
 */
class PolledOrderTest {

    private static final Instant ORDERED_AT = Instant.parse("2026-06-19T01:00:00Z");
    private static final String RAW = "{\"id\":\"o-1\"}";

    @Test
    void 생성된_record_는_필드를_보존한다() {
        // when
        PolledOrder polled = new PolledOrder(Channel.TOSS, "o-1", ORDERED_AT, RAW);

        // then
        assertThat(polled.channel()).isEqualTo(Channel.TOSS);
        assertThat(polled.externalOrderProductId()).isEqualTo("o-1");
        assertThat(polled.orderedAt()).isEqualTo(ORDERED_AT);
        assertThat(polled.rawPayload()).isEqualTo(RAW);
    }

    @Test
    void 생성자는_channel_이_null_이면_NPE() {
        assertThatThrownBy(() -> new PolledOrder(null, "o-1", ORDERED_AT, RAW))
                .isInstanceOf(NullPointerException.class)
                .hasMessage("channel");
    }

    @Test
    void 생성자는_externalOrderProductId_이_null_이면_NPE() {
        assertThatThrownBy(() -> new PolledOrder(Channel.TOSS, null, ORDERED_AT, RAW))
                .isInstanceOf(NullPointerException.class)
                .hasMessage("externalOrderProductId");
    }

    @Test
    void 생성자는_orderedAt_이_null_이면_NPE() {
        assertThatThrownBy(() -> new PolledOrder(Channel.TOSS, "o-1", null, RAW))
                .isInstanceOf(NullPointerException.class)
                .hasMessage("orderedAt");
    }

    @Test
    void 생성자는_rawPayload_이_null_이면_NPE() {
        assertThatThrownBy(() -> new PolledOrder(Channel.TOSS, "o-1", ORDERED_AT, null))
                .isInstanceOf(NullPointerException.class)
                .hasMessage("rawPayload");
    }
}
