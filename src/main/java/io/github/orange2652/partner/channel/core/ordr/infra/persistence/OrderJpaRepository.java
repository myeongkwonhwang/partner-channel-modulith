package io.github.orange2652.partner.channel.core.ordr.infra.persistence;

import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

interface OrderJpaRepository extends JpaRepository<OrderJpaEntity, Long> {

    Optional<OrderJpaEntity> findByChannelAndExternalOrderProductId(String channel, String externalOrderProductId);
}
