package io.github.orange2652.partner.channel.shared.event.ordr;

import java.util.Objects;
import java.util.UUID;

/**
 * A1 보상 — unconfirmedOrder(step1) 보상 결과. adapter 발행 → saga 수신.
 *
 * <p>FAILED 면 saga 는 COMPENSATING 을 유지하고 재시도한다 (R4).</p>
 *
 * @param sagaId 상관 키
 * @param result OK / FAILED
 */
public record UnconfirmedOrderCompensated(
        UUID sagaId,
        Result result
) {
    public enum Result {
        OK, FAILED
    }

    public UnconfirmedOrderCompensated {
        Objects.requireNonNull(sagaId, "sagaId");
        Objects.requireNonNull(result, "result");
    }

    public static UnconfirmedOrderCompensated ok(UUID sagaId) {
        return new UnconfirmedOrderCompensated(sagaId, Result.OK);
    }

    public static UnconfirmedOrderCompensated failed(UUID sagaId) {
        return new UnconfirmedOrderCompensated(sagaId, Result.FAILED);
    }
}
