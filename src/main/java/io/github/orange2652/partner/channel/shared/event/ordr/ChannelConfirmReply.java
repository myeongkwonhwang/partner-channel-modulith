package io.github.orange2652.partner.channel.shared.event.ordr;

import java.util.Objects;
import java.util.UUID;

/**
 * A1 step3 — channelConfirm reply. adapter 발행 → saga 수신.
 *
 * @param sagaId       상관 키
 * @param result       OK / FAILED
 * @param errorCode    FAILED 시 식별 코드
 * @param errorMessage FAILED 시 상세
 */
public record ChannelConfirmReply(
        UUID sagaId,
        Result result,
        String errorCode,
        String errorMessage
) {
    public enum Result {
        OK, FAILED
    }

    public ChannelConfirmReply {
        Objects.requireNonNull(sagaId, "sagaId");
        Objects.requireNonNull(result, "result");
    }

    public static ChannelConfirmReply ok(UUID sagaId) {
        return new ChannelConfirmReply(sagaId, Result.OK, null, null);
    }

    public static ChannelConfirmReply failed(UUID sagaId, String errorCode, String errorMessage) {
        return new ChannelConfirmReply(sagaId, Result.FAILED, errorCode, errorMessage);
    }
}
