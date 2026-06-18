package io.github.orange2652.partner.channel.shared.idempotency;

import java.io.Serializable;
import java.util.Objects;

/**
 * {@code processed_event} 복합키 클래스 ({@code @IdClass}) — {@code (consumerName, eventId)}.
 *
 * <p>shared 의 {@code @MappedSuperclass} {@link ProcessedEventJpaEntity} 가 공유하며, 각 모듈 infra 의
 * {@code @Entity} 구현이 그대로 상속한다.</p>
 */
public class ProcessedEventId implements Serializable {

    private String consumerName;
    private String eventId;

    protected ProcessedEventId() {
    }

    public ProcessedEventId(String consumerName, String eventId) {
        this.consumerName = consumerName;
        this.eventId = eventId;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof ProcessedEventId that)) {
            return false;
        }
        return Objects.equals(consumerName, that.consumerName) && Objects.equals(eventId, that.eventId);
    }

    @Override
    public int hashCode() {
        return Objects.hash(consumerName, eventId);
    }
}
