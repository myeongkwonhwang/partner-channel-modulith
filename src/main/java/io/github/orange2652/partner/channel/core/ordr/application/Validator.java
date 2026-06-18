package io.github.orange2652.partner.channel.core.ordr.application;

import io.github.orange2652.partner.channel.shared.domain.Channel;
import org.springframework.stereotype.Component;

/**
 * A1 step2 — 판매가능 비즈니스 검증 (read-only).
 *
 * <p>Phase 1 골격: 채널 지원 여부까지만 판정한다. raw 의 판매룰(예: 토스 {@code orderProductStatus == "PAID"},
 * 재고/가격/상품활성화)은 listener 와이어링과 함께 <b>Phase 2</b> 에서 채운다 — 거기서 Jackson 3 파서로 raw 를
 * 해석한다. REJECTED 는 양사 데이터 불일치 신호일 수 있어 알림+게이팅 대상(R4 D6/D7).</p>
 */
@Component
public class Validator {

    /** raw 판매룰은 Phase 2. 현재는 채널 지원 여부만. */
    public Verdict validate(String channel, String raw) {
        if (Channel.fromCode(channel).isEmpty()) {
            return Verdict.unsupportedChannel(channel);
        }
        // TODO(Phase 2): raw 파싱 후 채널별 판매룰 적용 (토스 orderProductStatus == PAID 등)
        return Verdict.passed();
    }

    /**
     * 검증 결과. PASSED / REJECTED + 사유.
     *
     * @param sellable      판매가능 여부
     * @param reasonCode    REJECTED 시 사유 코드
     * @param reasonMessage REJECTED 시 상세
     */
    public record Verdict(boolean sellable, String reasonCode, String reasonMessage) {

        public static Verdict passed() {
            return new Verdict(true, null, null);
        }

        public static Verdict rejected(String reasonCode, String reasonMessage) {
            return new Verdict(false, reasonCode, reasonMessage);
        }

        public static Verdict unsupportedChannel(String channel) {
            return new Verdict(false, "UNSUPPORTED_CHANNEL", "channel=" + channel);
        }
    }
}
