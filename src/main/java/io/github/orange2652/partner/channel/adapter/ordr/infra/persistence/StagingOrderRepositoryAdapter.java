package io.github.orange2652.partner.channel.adapter.ordr.infra.persistence;

import io.github.orange2652.partner.channel.adapter.ordr.domain.StagingOrder;
import io.github.orange2652.partner.channel.adapter.ordr.domain.StagingOrderRepository;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
class StagingOrderRepositoryAdapter implements StagingOrderRepository {

    private final StagingOrderJpaRepository jpaRepository;

    @Override
    public StagingOrder save(StagingOrder stagingOrder) {
        return jpaRepository.save(StagingOrderJpaEntity.from(stagingOrder)).toDomain();
    }

    @Override
    public Optional<StagingOrder> findById(Long id) {
        return jpaRepository.findById(id).map(StagingOrderJpaEntity::toDomain);
    }

    @Override
    public Optional<StagingOrder> findByChannelAndExternalOrderProductId(String channel, String externalOrderProductId) {
        return jpaRepository.findByChannelAndExternalOrderProductId(channel, externalOrderProductId)
                .map(StagingOrderJpaEntity::toDomain);
    }
}
