/**
 * sagaOrchestrator 모듈 — A1 / B1 saga 인스턴스 수명 + step advance + reconciliation / timeout scanner.
 *
 * <p>MSA 의 services/saga-orchestrator 와 1:1 매핑. saga_schema 의 saga_state 영속.
 * Application Events 로 다른 모듈에 command event publish + reply event 수신 → advance.
 * ADR-0003 (TIMEOUT) / ADR-0004 (Pivot reconciliation) / ADR-0005 (DLQ) 코드 위치.
 */
@org.springframework.modulith.ApplicationModule(
    displayName = "Saga Orchestrator",
    allowedDependencies = { "shared" }
)
package io.github.orange2652.partner.channel.saga;
