package io.github.orange2652.partner.channel.saga.ordr.infra.idempotency;

import io.github.orange2652.partner.channel.shared.idempotency.ProcessedEventJpaEntity;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;

/**
 * saga_schema 의 {@code processed_event} 물리 매핑 (R3 D1 / D-3) — saga 모듈 로컬.
 *
 * <p>공통 구조는 shared {@link ProcessedEventJpaEntity}(@MappedSuperclass) 가, 물리 위치(schema)는 본 final
 * {@code @Entity} 의 {@code @Table(schema)} 가 표현한다. saga 진입(sagaStart) 멱등 가드 등에 쓴다 —
 * core_schema / channel_schema 와 동일 구조의 별도 물리 테이블(통합 X, R3 D1).</p>
 */
@Entity
@Table(name = "processed_event", schema = "saga_schema")
final class SagaProcessedEventJpaEntity extends ProcessedEventJpaEntity {

    protected SagaProcessedEventJpaEntity() {
        super();
    }
}
