package io.github.orange2652.partner.channel.adapter.ordr.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.LocalDateTime;
import org.junit.jupiter.api.Test;

/**
 * {@link StagingOrder} 도메인 순수 단위 테스트 (프레임워크 0).
 *
 * <p>검증 불변식: ① 필수 필드 {@code requireNonNull} 가드, ② {@code newRecord} 정적 팩토리(신규 id=null,
 * status=ACTIVE, 시각 채움), ③ {@code canceled()} 불변 전이(새 인스턴스·status=CANCELED·원본 보존),
 * ④ 이미 CANCELED 면 자기 자신 반환(멱등 NoOp), ⑤ {@code isCanceled()}. 시각은 {@code now()} 를 직접
 * 단언하지 않고 변경 여부만 본다(결정적).</p>
 */
class StagingOrderTest {

    private static final String CHANNEL = "TOSS";
    private static final String EXT_ID = "o-1";
    private static final String RAW = "{\"orderProductId\":\"o-1\",\"status\":\"PAID\"}";
    private static final LocalDateTime TS = LocalDateTime.of(2026, 6, 19, 10, 0, 0);

    @Test
    void 생성자는_channel_이_null_이면_NPE() {
        assertThatThrownBy(() -> new StagingOrder(1L, null, EXT_ID, RAW, "ACTIVE", TS, TS))
                .isInstanceOf(NullPointerException.class)
                .hasMessage("channel");
    }

    @Test
    void 생성자는_externalOrderProductId_이_null_이면_NPE() {
        assertThatThrownBy(() -> new StagingOrder(1L, CHANNEL, null, RAW, "ACTIVE", TS, TS))
                .isInstanceOf(NullPointerException.class)
                .hasMessage("externalOrderProductId");
    }

    @Test
    void 생성자는_raw_가_null_이면_NPE() {
        assertThatThrownBy(() -> new StagingOrder(1L, CHANNEL, EXT_ID, null, "ACTIVE", TS, TS))
                .isInstanceOf(NullPointerException.class)
                .hasMessage("raw");
    }

    @Test
    void 생성자는_status_가_null_이면_NPE() {
        assertThatThrownBy(() -> new StagingOrder(1L, CHANNEL, EXT_ID, RAW, null, TS, TS))
                .isInstanceOf(NullPointerException.class)
                .hasMessage("status");
    }

    @Test
    void newRecord_는_id_null_status_ACTIVE_이고_시각을_채운다() {
        // when
        StagingOrder staging = StagingOrder.newRecord(CHANNEL, EXT_ID, RAW);

        // then
        assertThat(staging.id()).isNull();
        assertThat(staging.channel()).isEqualTo(CHANNEL);
        assertThat(staging.externalOrderProductId()).isEqualTo(EXT_ID);
        assertThat(staging.raw()).isEqualTo(RAW);
        assertThat(staging.status()).isEqualTo("ACTIVE");
        assertThat(staging.isCanceled()).isFalse();
        assertThat(staging.receivedAt()).isNotNull();
        assertThat(staging.updatedAt()).isNotNull();
    }

    @Test
    void canceled_는_새_인스턴스를_CANCELED_로_전이하고_원본은_불변() {
        // given
        StagingOrder original = new StagingOrder(7L, CHANNEL, EXT_ID, RAW, "ACTIVE", TS, TS);

        // when
        StagingOrder canceled = original.canceled();

        // then — 새 인스턴스, status 만 전이, id·channel·ext·raw·receivedAt 보존
        assertThat(canceled).isNotSameAs(original);
        assertThat(canceled.id()).isEqualTo(7L);
        assertThat(canceled.channel()).isEqualTo(CHANNEL);
        assertThat(canceled.externalOrderProductId()).isEqualTo(EXT_ID);
        assertThat(canceled.raw()).isEqualTo(RAW);
        assertThat(canceled.receivedAt()).isEqualTo(TS);
        assertThat(canceled.status()).isEqualTo("CANCELED");
        assertThat(canceled.isCanceled()).isTrue();
        // 원본 불변
        assertThat(original.status()).isEqualTo("ACTIVE");
    }

    @Test
    void canceled_는_updatedAt_을_갱신한다() {
        // given — updatedAt 을 과거로 둔 staging
        LocalDateTime stale = LocalDateTime.of(2000, 1, 1, 0, 0, 0);
        StagingOrder original = new StagingOrder(7L, CHANNEL, EXT_ID, RAW, "ACTIVE", TS, stale);

        // when
        StagingOrder canceled = original.canceled();

        // then
        assertThat(canceled.updatedAt()).isAfter(stale);
    }

    @Test
    void canceled_는_이미_CANCELED_면_자기_자신을_반환한다_멱등() {
        // given
        StagingOrder alreadyCanceled = new StagingOrder(7L, CHANNEL, EXT_ID, RAW, "CANCELED", TS, TS);

        // when
        StagingOrder result = alreadyCanceled.canceled();

        // then — 멱등 NoOp: 동일 인스턴스
        assertThat(result).isSameAs(alreadyCanceled);
    }
}
