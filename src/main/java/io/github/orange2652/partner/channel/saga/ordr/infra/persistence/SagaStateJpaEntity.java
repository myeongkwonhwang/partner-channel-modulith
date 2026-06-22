package io.github.orange2652.partner.channel.saga.ordr.infra.persistence;

import io.github.orange2652.partner.channel.saga.ordr.domain.SagaState;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.LocalDateTime;
import java.util.UUID;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/**
 * JPA Entity — {@code saga_schema.saga_state} 매핑. schema 라우팅은 {@code @Table(schema)} 로만 표현(D-3).
 *
 * <p>{@code payload} 는 Postgres {@code jsonb}(nullable) — {@code @JdbcTypeCode(JSON)} 으로 String↔jsonb 매핑.
 * package-private — 외부에서는 도메인 record + Port 로만 접근.</p>
 */
@Entity
@Table(name = "saga_state", schema = "saga_schema")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor(access = AccessLevel.PRIVATE)
@Builder
class SagaStateJpaEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "saga_id", nullable = false, unique = true)
    private UUID sagaId;

    @Column(name = "saga_type", nullable = false, length = 32)
    private String sagaType;

    @Column(name = "correlation_key", nullable = false, length = 128)
    private String correlationKey;

    @Column(name = "current_step", nullable = false, length = 64)
    private String currentStep;

    @Column(name = "status", nullable = false, length = 32)
    private String status;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "payload", columnDefinition = "jsonb")
    private String payload;

    @Column(name = "reconciliation_attempts", nullable = false)
    private int reconciliationAttempts;

    @Column(name = "started_at", nullable = false)
    private LocalDateTime startedAt;

    @Column(name = "last_transition_at", nullable = false)
    private LocalDateTime lastTransitionAt;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    static SagaStateJpaEntity from(SagaState sagaState) {
        return SagaStateJpaEntity.builder()
                .id(sagaState.id())
                .sagaId(sagaState.sagaId())
                .sagaType(sagaState.sagaType())
                .correlationKey(sagaState.correlationKey())
                .currentStep(sagaState.currentStep())
                .status(sagaState.status())
                .payload(sagaState.payload())
                .reconciliationAttempts(sagaState.reconciliationAttempts())
                .startedAt(sagaState.startedAt())
                .lastTransitionAt(sagaState.lastTransitionAt())
                .updatedAt(sagaState.updatedAt())
                .build();
    }

    SagaState toDomain() {
        return new SagaState(id, sagaId, sagaType, correlationKey, currentStep, status, payload,
                reconciliationAttempts, startedAt, lastTransitionAt, updatedAt);
    }
}
