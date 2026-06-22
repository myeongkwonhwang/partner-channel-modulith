package io.github.orange2652.partner.channel.saga.ordr.infra.persistence;

import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

interface SagaStateJpaRepository extends JpaRepository<SagaStateJpaEntity, Long> {

    Optional<SagaStateJpaEntity> findBySagaId(UUID sagaId);

    Optional<SagaStateJpaEntity> findByCorrelationKey(String correlationKey);
}
