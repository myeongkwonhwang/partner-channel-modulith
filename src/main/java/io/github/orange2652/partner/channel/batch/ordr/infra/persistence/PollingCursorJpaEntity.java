package io.github.orange2652.partner.channel.batch.ordr.infra.persistence;

import io.github.orange2652.partner.channel.batch.ordr.domain.PollingCursor;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.LocalDateTime;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * JPA Entity — {@code channel_schema.polling_cursor} 매핑. schema 라우팅은 {@code @Table(schema)} 로만 표현(D-3).
 *
 * <p>package-private — 외부에서는 도메인 record + Port 로만 접근.</p>
 */
@Entity
@Table(name = "polling_cursor", schema = "channel_schema")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor(access = AccessLevel.PRIVATE)
@Builder
class PollingCursorJpaEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "channel", nullable = false, length = 32)
    private String channel;

    @Column(name = "cursor_type", nullable = false, length = 32)
    private String cursorType;

    @Column(name = "last_polled_at", nullable = false)
    private LocalDateTime lastPolledAt;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    static PollingCursorJpaEntity from(PollingCursor cursor) {
        return PollingCursorJpaEntity.builder()
                .id(cursor.id())
                .channel(cursor.channel())
                .cursorType(cursor.cursorType())
                .lastPolledAt(cursor.lastPolledAt())
                .updatedAt(cursor.updatedAt())
                .build();
    }

    PollingCursor toDomain() {
        return new PollingCursor(id, channel, cursorType, lastPolledAt, updatedAt);
    }
}
