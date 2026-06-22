package io.github.orange2652.partner.channel.saga.ordr.infra.idempotency;

import io.github.orange2652.partner.channel.shared.idempotency.ProcessedEventId;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

interface SagaProcessedEventJpaRepository extends JpaRepository<SagaProcessedEventJpaEntity, ProcessedEventId> {

    /**
     * 원자 멱등 가드 (R3) — {@code ON CONFLICT DO NOTHING}. JPA {@code save()}(select-then-insert) 의 race 회피.
     *
     * @return 영향 행 수 — 1 이면 신규 마킹, 0 이면 이미 처리됨.
     */
    @Modifying
    @Query(value = """
            INSERT INTO saga_schema.processed_event (consumer_name, event_id, processed_at)
            VALUES (:consumerName, :eventId, now())
            ON CONFLICT (consumer_name, event_id) DO NOTHING
            """, nativeQuery = true)
    int insertIfAbsent(@Param("consumerName") String consumerName, @Param("eventId") String eventId);
}
