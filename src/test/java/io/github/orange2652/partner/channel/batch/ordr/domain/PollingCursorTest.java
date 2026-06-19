package io.github.orange2652.partner.channel.batch.ordr.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.LocalDateTime;
import org.junit.jupiter.api.Test;

/**
 * {@link PollingCursor} 도메인 순수 단위 테스트 (프레임워크 0).
 *
 * <p>검증 불변식: ① 필수 필드 {@code requireNonNull} 가드, ② {@code initial} 정적 팩토리(신규는 id=null,
 * updatedAt 채움), ③ {@code advanceTo} 불변 전이(새 인스턴스, lastPolledAt 만 변경, 원본 보존).
 * 시각은 {@code LocalDateTime.now()} 를 직접 단언하지 않고 변경 여부·동등성만 본다(결정적).</p>
 */
class PollingCursorTest {

    private static final String CHANNEL = "TOSS";
    private static final String CURSOR_TYPE = "ORDER";
    private static final LocalDateTime LAST_POLLED_AT = LocalDateTime.of(2026, 6, 19, 10, 0, 0);

    @Test
    void 생성자는_channel_이_null_이면_NPE() {
        assertThatThrownBy(() -> new PollingCursor(1L, null, CURSOR_TYPE, LAST_POLLED_AT, LAST_POLLED_AT))
                .isInstanceOf(NullPointerException.class)
                .hasMessage("channel");
    }

    @Test
    void 생성자는_cursorType_이_null_이면_NPE() {
        assertThatThrownBy(() -> new PollingCursor(1L, CHANNEL, null, LAST_POLLED_AT, LAST_POLLED_AT))
                .isInstanceOf(NullPointerException.class)
                .hasMessage("cursorType");
    }

    @Test
    void 생성자는_lastPolledAt_이_null_이면_NPE() {
        assertThatThrownBy(() -> new PollingCursor(1L, CHANNEL, CURSOR_TYPE, null, LAST_POLLED_AT))
                .isInstanceOf(NullPointerException.class)
                .hasMessage("lastPolledAt");
    }

    @Test
    void 생성자는_id_와_updatedAt_은_null_허용() {
        // id=null 은 신규(미채번), updatedAt 은 가드 대상 아님 — 생성 성공만 단언.
        PollingCursor cursor = new PollingCursor(null, CHANNEL, CURSOR_TYPE, LAST_POLLED_AT, null);

        assertThat(cursor.id()).isNull();
        assertThat(cursor.updatedAt()).isNull();
    }

    @Test
    void initial_은_id_null_이고_updatedAt_을_채운다() {
        // when
        PollingCursor cursor = PollingCursor.initial(CHANNEL, CURSOR_TYPE, LAST_POLLED_AT);

        // then
        assertThat(cursor.id()).isNull();
        assertThat(cursor.channel()).isEqualTo(CHANNEL);
        assertThat(cursor.cursorType()).isEqualTo(CURSOR_TYPE);
        assertThat(cursor.lastPolledAt()).isEqualTo(LAST_POLLED_AT);
        assertThat(cursor.updatedAt()).isNotNull();
    }

    @Test
    void advanceTo_는_새_인스턴스를_반환하고_원본은_불변() {
        // given
        PollingCursor original = new PollingCursor(7L, CHANNEL, CURSOR_TYPE, LAST_POLLED_AT, LAST_POLLED_AT);
        LocalDateTime next = LAST_POLLED_AT.plusMinutes(30);

        // when
        PollingCursor advanced = original.advanceTo(next);

        // then — 새 인스턴스, lastPolledAt 만 전진, id·channel·cursorType 보존
        assertThat(advanced).isNotSameAs(original);
        assertThat(advanced.id()).isEqualTo(7L);
        assertThat(advanced.channel()).isEqualTo(CHANNEL);
        assertThat(advanced.cursorType()).isEqualTo(CURSOR_TYPE);
        assertThat(advanced.lastPolledAt()).isEqualTo(next);
        // 원본 불변
        assertThat(original.lastPolledAt()).isEqualTo(LAST_POLLED_AT);
    }

    @Test
    void advanceTo_는_updatedAt_을_갱신한다() {
        // given — updatedAt 을 과거로 둔 cursor
        LocalDateTime staleUpdatedAt = LocalDateTime.of(2000, 1, 1, 0, 0, 0);
        PollingCursor original = new PollingCursor(7L, CHANNEL, CURSOR_TYPE, LAST_POLLED_AT, staleUpdatedAt);

        // when
        PollingCursor advanced = original.advanceTo(LAST_POLLED_AT.plusMinutes(30));

        // then — now 로 갱신되어 과거값보다 이후
        assertThat(advanced.updatedAt()).isAfter(staleUpdatedAt);
    }

    @Test
    void advanceTo_는_nextLastPolledAt_이_null_이면_NPE() {
        PollingCursor original = PollingCursor.initial(CHANNEL, CURSOR_TYPE, LAST_POLLED_AT);

        assertThatThrownBy(() -> original.advanceTo(null))
                .isInstanceOf(NullPointerException.class)
                .hasMessage("nextLastPolledAt");
    }
}
