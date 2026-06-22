package io.github.orange2652.partner.channel.adapter.ordr.infra.toss;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.orange2652.partner.channel.shared.domain.ExternalOrderStatus;
import org.junit.jupiter.api.Test;

/**
 * {@link TossOrderStatus} 단위 테스트 — raw 19값 → 추상 {@link ExternalOrderStatus} 매핑(R6).
 */
class TossOrderStatusTest {

    @Test
    void PAID_는_PAID_로_매핑된다() {
        assertThat(TossOrderStatus.PAID.toExternal()).isEqualTo(ExternalOrderStatus.PAID);
    }

    @Test
    void PREPARING_PRODUCT_는_ACCEPTED_로_매핑된다() {
        assertThat(TossOrderStatus.PREPARING_PRODUCT.toExternal()).isEqualTo(ExternalOrderStatus.ACCEPTED);
    }

    @Test
    void CANCELED_PAYMENT_는_CANCELED_로_매핑된다() {
        assertThat(TossOrderStatus.CANCELED_PAYMENT.toExternal()).isEqualTo(ExternalOrderStatus.CANCELED);
    }

    @Test
    void 그_외_상태는_OTHER_로_매핑된다() {
        assertThat(TossOrderStatus.DELIVERING.toExternal()).isEqualTo(ExternalOrderStatus.OTHER);
        assertThat(TossOrderStatus.CONFIRMED_ORDER.toExternal()).isEqualTo(ExternalOrderStatus.OTHER);
        assertThat(TossOrderStatus.BEFORE_PAYMENT.toExternal()).isEqualTo(ExternalOrderStatus.OTHER);
        assertThat(TossOrderStatus.REQUESTED_RETURN.toExternal()).isEqualTo(ExternalOrderStatus.OTHER);
    }

    @Test
    void fromCode_는_유효코드는_매핑하고_미지원은_empty() {
        assertThat(TossOrderStatus.fromCode("PAID")).contains(TossOrderStatus.PAID);
        assertThat(TossOrderStatus.fromCode("NOPE")).isEmpty();
        assertThat(TossOrderStatus.fromCode(null)).isEmpty();
    }
}
