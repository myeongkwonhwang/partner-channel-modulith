-- saga_schema baseline (sagaOrchestrator 모듈)
-- saga_state — sagaId, type, correlationKey, currentStep, status, payload, reconciliation_attempts, last_transition_at
-- (MSA V1 + V3 + V4 통합 baseline)

CREATE TABLE saga_schema.saga_state (
    id                          BIGSERIAL PRIMARY KEY,
    saga_id                     UUID         NOT NULL UNIQUE,
    saga_type                   VARCHAR(32)  NOT NULL,
    correlation_key             VARCHAR(128) NOT NULL,
    current_step                VARCHAR(64)  NOT NULL,
    status                      VARCHAR(32)  NOT NULL DEFAULT 'RUNNING',
    payload                     JSONB,
    reconciliation_attempts     INT          NOT NULL DEFAULT 0,
    started_at                  TIMESTAMP    NOT NULL DEFAULT now(),
    last_transition_at          TIMESTAMP    NOT NULL DEFAULT now(),
    updated_at                  TIMESTAMP    NOT NULL DEFAULT now()
);
CREATE INDEX idx_saga_state_correlation ON saga_schema.saga_state(correlation_key);
CREATE INDEX idx_saga_state_status_step_ts ON saga_schema.saga_state(status, current_step, last_transition_at);
CREATE INDEX idx_saga_state_pending_reconciliation
    ON saga_schema.saga_state(reconciliation_attempts, last_transition_at)
    WHERE current_step = 'CONFIRMED_ORDER_PENDING_RECONCILIATION';
