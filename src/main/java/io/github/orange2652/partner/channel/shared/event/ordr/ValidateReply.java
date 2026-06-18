package io.github.orange2652.partner.channel.shared.event.ordr;

import java.util.Objects;
import java.util.UUID;

/**
 * A1 step2 — validate reply. core 발행 → saga 수신.
 *
 * <p>REJECTED(판매 불가)는 비즈니스 실패 — saga 는 step1 보상으로 전이한다 (R4).</p>
 *
 * @param sagaId       상관 키
 * @param result       PASSED / REJECTED
 * @param reasonCode   REJECTED 시 사유 코드
 * @param reasonMessage REJECTED 시 상세
 */
public record ValidateReply(
        UUID sagaId,
        Result result,
        String reasonCode,
        String reasonMessage
) {
    public enum Result {
        PASSED, REJECTED
    }

    public ValidateReply {
        Objects.requireNonNull(sagaId, "sagaId");
        Objects.requireNonNull(result, "result");
    }

    public static ValidateReply passed(UUID sagaId) {
        return new ValidateReply(sagaId, Result.PASSED, null, null);
    }

    public static ValidateReply rejected(UUID sagaId, String reasonCode, String reasonMessage) {
        return new ValidateReply(sagaId, Result.REJECTED, reasonCode, reasonMessage);
    }
}
