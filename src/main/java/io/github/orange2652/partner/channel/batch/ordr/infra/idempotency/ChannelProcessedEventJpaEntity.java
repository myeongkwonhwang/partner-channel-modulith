package io.github.orange2652.partner.channel.batch.ordr.infra.idempotency;

import io.github.orange2652.partner.channel.shared.idempotency.ProcessedEventJpaEntity;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;

/**
 * channel_schema 의 {@code processed_event} 물리 매핑 (R3 D1 / D-3).
 *
 * <p>공통 구조는 shared {@link ProcessedEventJpaEntity}(@MappedSuperclass) 가, 물리 위치(schema)는 본 final
 * {@code @Entity} 의 {@code @Table(schema)} 가 표현한다. batch 모듈 로컬 — core_schema 복제(통합 X, R3 D1).</p>
 */
@Entity
@Table(name = "processed_event", schema = "channel_schema")
final class ChannelProcessedEventJpaEntity extends ProcessedEventJpaEntity {

    protected ChannelProcessedEventJpaEntity() {
        super();
    }
}
