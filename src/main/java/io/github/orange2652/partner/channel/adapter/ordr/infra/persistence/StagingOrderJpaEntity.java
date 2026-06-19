package io.github.orange2652.partner.channel.adapter.ordr.infra.persistence;

import io.github.orange2652.partner.channel.adapter.ordr.domain.StagingOrder;
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
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/**
 * JPA Entity — {@code channel_schema.staging_order} 매핑. schema 라우팅은 {@code @Table(schema)} 로만 표현(D-3).
 *
 * <p>{@code raw} 는 Postgres {@code jsonb} 컬럼 — Hibernate {@code @JdbcTypeCode(JSON)} 으로 String↔jsonb
 * 매핑(파싱은 Phase 2 Validator 책임, 여기선 원본 보관만). package-private — 외부에서는 도메인 record + Port 로만
 * 접근.</p>
 */
@Entity
@Table(name = "staging_order", schema = "channel_schema")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor(access = AccessLevel.PRIVATE)
@Builder
class StagingOrderJpaEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "channel", nullable = false, length = 32)
    private String channel;

    @Column(name = "external_order_product_id", nullable = false, length = 64)
    private String externalOrderProductId;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "raw", nullable = false, columnDefinition = "jsonb")
    private String raw;

    @Column(name = "status", nullable = false, length = 32)
    private String status;

    @Column(name = "received_at", nullable = false)
    private LocalDateTime receivedAt;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    static StagingOrderJpaEntity from(StagingOrder stagingOrder) {
        return StagingOrderJpaEntity.builder()
                .id(stagingOrder.id())
                .channel(stagingOrder.channel())
                .externalOrderProductId(stagingOrder.externalOrderProductId())
                .raw(stagingOrder.raw())
                .status(stagingOrder.status())
                .receivedAt(stagingOrder.receivedAt())
                .updatedAt(stagingOrder.updatedAt())
                .build();
    }

    StagingOrder toDomain() {
        return new StagingOrder(id, channel, externalOrderProductId, raw, status, receivedAt, updatedAt);
    }
}
