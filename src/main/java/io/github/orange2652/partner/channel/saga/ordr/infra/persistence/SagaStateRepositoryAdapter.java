package io.github.orange2652.partner.channel.saga.ordr.infra.persistence;

import io.github.orange2652.partner.channel.saga.ordr.domain.SagaState;
import io.github.orange2652.partner.channel.saga.ordr.domain.SagaStateRepository;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
class SagaStateRepositoryAdapter implements SagaStateRepository {

    private final SagaStateJpaRepository jpaRepository;

    @Override
    public SagaState save(SagaState sagaState) {
        return jpaRepository.save(SagaStateJpaEntity.from(sagaState)).toDomain();
    }

    @Override
    public Optional<SagaState> findBySagaId(UUID sagaId) {
        return jpaRepository.findBySagaId(sagaId).map(SagaStateJpaEntity::toDomain);
    }

    @Override
    public Optional<SagaState> findByCorrelationKey(String correlationKey) {
        return jpaRepository.findByCorrelationKey(correlationKey).map(SagaStateJpaEntity::toDomain);
    }

    @Override
    public List<SagaState> findStuck(String status, LocalDateTime threshold) {
        return jpaRepository.findByStatusAndLastTransitionAtBefore(status, threshold).stream()
                .map(SagaStateJpaEntity::toDomain)
                .toList();
    }
}
