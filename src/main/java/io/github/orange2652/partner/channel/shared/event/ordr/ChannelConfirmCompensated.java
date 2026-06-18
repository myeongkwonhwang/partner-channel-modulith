package io.github.orange2652.partner.channel.shared.event.ordr;

import java.util.Objects;
import java.util.UUID;

/**
 * A1 보상 — channelConfirm(step3) 보상 결과. adapter 발행 → saga 수신.
 *
 * <p>{@link Result#MANUAL_REQUIRED} (R4): 외부 cancel API 미가용/실패 시 saga 는 {@code PENDING_MANUAL_CANCEL}
 * terminal 로 전이하고 운영 개입을 기다린다. FAILED 면 COMPENSATING 유지 후 재시도.</p>
 *
 * @param sagaId 상관 키
 * @param result OK / MANUAL_REQUIRED / FAILED
 */
public record ChannelConfirmCompensated(
        UUID sagaId,
        Result result
) {
    public enum Result {
        OK, MANUAL_REQUIRED, FAILED
    }

    public ChannelConfirmCompensated {
        Objects.requireNonNull(sagaId, "sagaId");
        Objects.requireNonNull(result, "result");
    }

    public static ChannelConfirmCompensated ok(UUID sagaId) {
        return new ChannelConfirmCompensated(sagaId, Result.OK);
    }

    public static ChannelConfirmCompensated manualRequired(UUID sagaId) {
        return new ChannelConfirmCompensated(sagaId, Result.MANUAL_REQUIRED);
    }

    public static ChannelConfirmCompensated failed(UUID sagaId) {
        return new ChannelConfirmCompensated(sagaId, Result.FAILED);
    }
}
