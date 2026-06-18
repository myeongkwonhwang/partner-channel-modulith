package io.github.orange2652.partner.channel.shared.idempotency;

import jakarta.persistence.Column;
import jakarta.persistence.Id;
import jakarta.persistence.IdClass;
import jakarta.persistence.MappedSuperclass;
import java.time.LocalDateTime;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * {@code processed_event} 공통 JPA 매핑 (R3 D1 / D-3) — {@code @MappedSuperclass} 라 자체 테이블이 없다.
 *
 * <p>각 모듈 infra 의 {@code final @Entity @Table(schema = "...")} 구현이 본 클래스를 상속해
 * 자기 schema 의 물리 테이블에 1:1 매핑한다 (saga_schema / core_schema / channel_schema). 단일
 * DataSource·단일 EntityManagerFactory 에서 schema 라우팅은 구현체의 {@code @Table(schema)} 로만 표현한다.</p>
 */
@MappedSuperclass
@IdClass(ProcessedEventId.class)
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public abstract class ProcessedEventJpaEntity {

    @Id
    @Column(name = "consumer_name", nullable = false, length = 64)
    private String consumerName;

    @Id
    @Column(name = "event_id", nullable = false, length = 128)
    private String eventId;

    @Column(name = "processed_at", nullable = false)
    private LocalDateTime processedAt;

    protected ProcessedEventJpaEntity(String consumerName, String eventId, LocalDateTime processedAt) {
        this.consumerName = consumerName;
        this.eventId = eventId;
        this.processedAt = processedAt;
    }

    public ProcessedEvent toDomain() {
        return new ProcessedEvent(consumerName, eventId, processedAt);
    }
}
