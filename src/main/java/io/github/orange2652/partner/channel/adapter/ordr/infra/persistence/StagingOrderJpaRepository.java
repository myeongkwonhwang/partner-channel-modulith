package io.github.orange2652.partner.channel.adapter.ordr.infra.persistence;

import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

interface StagingOrderJpaRepository extends JpaRepository<StagingOrderJpaEntity, Long> {

    Optional<StagingOrderJpaEntity> findByChannelAndExternalOrderProductId(String channel, String externalOrderProductId);
}
