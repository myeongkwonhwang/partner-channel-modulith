package io.github.orange2652.partner.channel.adapter.ordr.infra.idempotency;

import io.github.orange2652.partner.channel.shared.idempotency.ProcessedEventJpaEntity;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;

/**
 * channel_schema 의 {@code processed_event} 물리 매핑 (R3 D1 / D-3) — adapter 모듈 로컬.
 *
 * <p>공통 구조는 shared {@link ProcessedEventJpaEntity}(@MappedSuperclass) 가, 물리 위치(schema)는 본 final
 * {@code @Entity} 의 {@code @Table(schema)} 가 표현한다. batch 모듈과 <b>같은 물리 테이블</b>
 * (channel_schema.processed_event)을 가리키되, 모듈이 각자 infra 를 소유하는 규율에 따라 모듈별 {@code @Entity}
 * 를 1벌씩 둔다 — 행은 {@code consumer_name}(adapter: unconfirmedOrder / channelConfirm / compensate:*) 으로
 * 갈린다. JPA 엔티티 이름 충돌을 피하려 batch 의 {@code ChannelProcessedEventJpaEntity} 와 다른 클래스명을 쓴다.</p>
 */
@Entity
@Table(name = "processed_event", schema = "channel_schema")
final class AdapterProcessedEventJpaEntity extends ProcessedEventJpaEntity {

    protected AdapterProcessedEventJpaEntity() {
        super();
    }
}
