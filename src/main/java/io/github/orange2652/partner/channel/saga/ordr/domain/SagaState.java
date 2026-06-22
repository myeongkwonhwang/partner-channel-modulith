package io.github.orange2652.partner.channel.saga.ordr.domain;

import java.time.LocalDateTime;
import java.util.Objects;
import java.util.UUID;

/**
 * A1 saga 인스턴스 상태 — {@code saga_schema.saga_state} 매핑. saga 수명(시작→step advance→종료/보상)을 보유.
 *
 * <p>{@code sagaId} UNIQUE — 인스턴스 1개. {@code correlationKey} = {@code "{channel}:{externalOrderProductId}"}
 * (EventKey)로 주문↔saga 를 잇는다. 불변 record — 상태 변경은 새 인스턴스를 반환한다(전이마다 {@code lastTransitionAt}
 * 갱신, R5a timeout/stuck scanner 의 SLA 기준점).</p>
 *
 * <p>실제 step 진행 규칙(어느 step 으로 언제 advance)·step/status 상수 집합은 Phase 2 오케스트레이션에서 정의한다.
 * 본 도메인은 전이 어휘({@code advanceTo} / {@code withStatus} / {@code incrementReconciliationAttempts})만 제공하고
 * step·status 값은 호출자가 넘긴다(매직 스트링은 Phase 2 상수/enum 으로).</p>
 *
 * @param id                      DB PK (신규는 null)
 * @param sagaId                  saga 인스턴스 식별자 — UNIQUE
 * @param sagaType               saga 종류 (A1 주문 / B1 송장 — Phase 2 상수)
 * @param correlationKey         상관 키 {@code "{channel}:{externalOrderProductId}"}
 * @param currentStep            현재 step
 * @param status                 인스턴스 상태 (시작 {@code "RUNNING"})
 * @param payload                saga 컨텍스트(JSON, nullable)
 * @param reconciliationAttempts Pivot 부분실패 reconciliation 시도 횟수 (R4/R5a)
 * @param startedAt              시작 시각
 * @param lastTransitionAt       마지막 전이 시각 (SLA 기준)
 * @param updatedAt              마지막 갱신 시각
 */
public record SagaState(
        Long id,
        UUID sagaId,
        String sagaType,
        String correlationKey,
        String currentStep,
        String status,
        String payload,
        int reconciliationAttempts,
        LocalDateTime startedAt,
        LocalDateTime lastTransitionAt,
        LocalDateTime updatedAt
) {
    private static final String STATUS_RUNNING = "RUNNING";

    public SagaState {
        Objects.requireNonNull(sagaId, "sagaId");
        Objects.requireNonNull(sagaType, "sagaType");
        Objects.requireNonNull(correlationKey, "correlationKey");
        Objects.requireNonNull(currentStep, "currentStep");
        Objects.requireNonNull(status, "status");
    }

    /** 새 saga 인스턴스 — {@code id=null}, {@code status="RUNNING"}, {@code reconciliationAttempts=0}, 시각은 now. */
    public static SagaState start(UUID sagaId, String sagaType, String correlationKey, String initialStep,
            String payload) {
        LocalDateTime now = LocalDateTime.now();
        return new SagaState(null, sagaId, sagaType, correlationKey, initialStep, STATUS_RUNNING, payload, 0,
                now, now, now);
    }

    /** 다음 step 으로 전진한 새 인스턴스(불변) — {@code lastTransitionAt}·{@code updatedAt} 갱신. */
    public SagaState advanceTo(String nextStep) {
        Objects.requireNonNull(nextStep, "nextStep");
        LocalDateTime now = LocalDateTime.now();
        return new SagaState(id, sagaId, sagaType, correlationKey, nextStep, status, payload,
                reconciliationAttempts, startedAt, now, now);
    }

    /** 상태를 전이한 새 인스턴스(불변) — 예: RUNNING→COMPENSATING/COMPLETED/FAILED/PENDING_MANUAL_CANCEL. */
    public SagaState withStatus(String newStatus) {
        Objects.requireNonNull(newStatus, "newStatus");
        LocalDateTime now = LocalDateTime.now();
        return new SagaState(id, sagaId, sagaType, correlationKey, currentStep, newStatus, payload,
                reconciliationAttempts, startedAt, now, now);
    }

    /** reconciliation 시도 횟수 +1 한 새 인스턴스(불변) — Pivot 부분실패 재시도(R4 N=3). */
    public SagaState incrementReconciliationAttempts() {
        LocalDateTime now = LocalDateTime.now();
        return new SagaState(id, sagaId, sagaType, correlationKey, currentStep, status, payload,
                reconciliationAttempts + 1, startedAt, now, now);
    }
}
