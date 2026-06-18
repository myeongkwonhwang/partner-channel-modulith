package io.github.orange2652.partner.channel.shared.event.ordr;

import java.util.Objects;
import java.util.UUID;

/**
 * A1 step4 — confirmedOrder reply (Pivot). core 발행 → saga 수신.
 *
 * <p>FAILED 시 {@link #outcome} 으로 분기 (R4/ADR-0004): {@link FailureOutcome#PENDING_RECONCILIATION}
 * 은 "외부 물류 OK + 내부 INSERT 실패" — worker 재시도 대상. {@link FailureOutcome#TERMINAL} 은 외부 호출
 * 자체 실패 — 즉시 terminal.</p>
 *
 * @param sagaId       상관 키
 * @param result       OK / FAILED
 * @param outcome      FAILED 시 분기. OK 시 null
 * @param orderId      OK 시 내부 주문 PK
 * @param shipmentId   OK 또는 PENDING_RECONCILIATION 시 외부 물류 식별자(보존)
 * @param errorCode    FAILED 시 식별 코드
 * @param errorMessage FAILED 시 상세
 */
public record ConfirmedOrderReply(
        UUID sagaId,
        Result result,
        FailureOutcome outcome,
        Long orderId,
        String shipmentId,
        String errorCode,
        String errorMessage
) {
    public enum Result {
        OK, FAILED
    }

    public enum FailureOutcome {
        /** 외부 물류 OK + 내부 INSERT 실패 — PENDING_RECONCILIATION 진입, worker 재시도. */
        PENDING_RECONCILIATION,
        /** 외부 호출 자체 실패/복구 불가 — 즉시 CONFIRMED_ORDER_FAILED terminal. */
        TERMINAL
    }

    public ConfirmedOrderReply {
        Objects.requireNonNull(sagaId, "sagaId");
        Objects.requireNonNull(result, "result");
    }

    public static ConfirmedOrderReply ok(UUID sagaId, Long orderId, String shipmentId) {
        return new ConfirmedOrderReply(sagaId, Result.OK, null, orderId, shipmentId, null, null);
    }

    public static ConfirmedOrderReply failedTerminal(UUID sagaId, String errorCode, String errorMessage) {
        return new ConfirmedOrderReply(sagaId, Result.FAILED, FailureOutcome.TERMINAL,
                null, null, errorCode, errorMessage);
    }

    public static ConfirmedOrderReply failedPendingReconciliation(UUID sagaId, String shipmentId,
                                                                  String errorCode, String errorMessage) {
        return new ConfirmedOrderReply(sagaId, Result.FAILED, FailureOutcome.PENDING_RECONCILIATION,
                null, shipmentId, errorCode, errorMessage);
    }
}
