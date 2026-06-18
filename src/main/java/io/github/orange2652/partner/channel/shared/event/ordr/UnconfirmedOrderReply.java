package io.github.orange2652.partner.channel.shared.event.ordr;

import java.util.Objects;
import java.util.UUID;

/**
 * A1 step1 — unconfirmedOrder reply. adapter 발행 → saga 수신해 state advance.
 *
 * @param sagaId       상관 키
 * @param result       OK / FAILED
 * @param stagingId    OK 시 staging row PK
 * @param errorCode    FAILED 시 식별 코드
 * @param errorMessage FAILED 시 상세
 */
public record UnconfirmedOrderReply(
        UUID sagaId,
        Result result,
        Long stagingId,
        String errorCode,
        String errorMessage
) {
    public enum Result {
        OK, FAILED
    }

    public UnconfirmedOrderReply {
        Objects.requireNonNull(sagaId, "sagaId");
        Objects.requireNonNull(result, "result");
    }

    public static UnconfirmedOrderReply ok(UUID sagaId, Long stagingId) {
        return new UnconfirmedOrderReply(sagaId, Result.OK, stagingId, null, null);
    }

    public static UnconfirmedOrderReply failed(UUID sagaId, String errorCode, String errorMessage) {
        return new UnconfirmedOrderReply(sagaId, Result.FAILED, null, errorCode, errorMessage);
    }
}
