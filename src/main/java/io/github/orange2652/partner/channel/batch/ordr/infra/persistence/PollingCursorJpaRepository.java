package io.github.orange2652.partner.channel.batch.ordr.infra.persistence;

import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

interface PollingCursorJpaRepository extends JpaRepository<PollingCursorJpaEntity, Long> {

    Optional<PollingCursorJpaEntity> findByChannelAndCursorType(String channel, String cursorType);
}
