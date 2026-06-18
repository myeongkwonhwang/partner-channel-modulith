package io.github.orange2652.partner.channel.core.ordr.infra.persistence;

import io.github.orange2652.partner.channel.core.ordr.domain.Order;
import io.github.orange2652.partner.channel.core.ordr.domain.OrderRepository;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
class OrderRepositoryAdapter implements OrderRepository {

    private final OrderJpaRepository jpaRepository;

    @Override
    public Order save(Order order) {
        return jpaRepository.save(OrderJpaEntity.from(order)).toDomain();
    }

    @Override
    public Optional<Order> findByChannelAndExternalOrderProductId(String channel, String externalOrderProductId) {
        return jpaRepository.findByChannelAndExternalOrderProductId(channel, externalOrderProductId)
                .map(OrderJpaEntity::toDomain);
    }
}
