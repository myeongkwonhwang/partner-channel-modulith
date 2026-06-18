package io.github.orange2652.partner.channel.core.ordr.infra.persistence;

import io.github.orange2652.partner.channel.core.ordr.domain.Order;
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

/**
 * JPA Entity — {@code core_schema.orders} 매핑. schema 라우팅은 {@code @Table(schema)} 로만 표현(D-3).
 *
 * <p>package-private — 외부에서는 도메인 record + Port 로만 접근.</p>
 */
@Entity
@Table(name = "orders", schema = "core_schema")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor(access = AccessLevel.PRIVATE)
@Builder
class OrderJpaEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "saga_id", nullable = false)
    private UUID sagaId;

    @Column(name = "channel", nullable = false, length = 32)
    private String channel;

    @Column(name = "external_order_product_id", nullable = false, length = 64)
    private String externalOrderProductId;

    @Column(name = "shipment_id", length = 64)
    private String shipmentId;

    @Column(name = "status", nullable = false, length = 32)
    private String status;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    static OrderJpaEntity from(Order order) {
        return OrderJpaEntity.builder()
                .id(order.id())
                .sagaId(order.sagaId())
                .channel(order.channel())
                .externalOrderProductId(order.externalOrderProductId())
                .shipmentId(order.shipmentId())
                .status(order.status())
                .createdAt(order.createdAt())
                .updatedAt(order.updatedAt())
                .build();
    }

    Order toDomain() {
        return new Order(id, sagaId, channel, externalOrderProductId, shipmentId, status, createdAt, updatedAt);
    }
}
