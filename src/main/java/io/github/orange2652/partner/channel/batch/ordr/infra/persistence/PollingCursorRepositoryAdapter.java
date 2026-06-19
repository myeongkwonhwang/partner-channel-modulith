package io.github.orange2652.partner.channel.batch.ordr.infra.persistence;

import io.github.orange2652.partner.channel.batch.ordr.domain.PollingCursor;
import io.github.orange2652.partner.channel.batch.ordr.domain.PollingCursorRepository;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
class PollingCursorRepositoryAdapter implements PollingCursorRepository {

    private final PollingCursorJpaRepository jpaRepository;

    @Override
    public PollingCursor save(PollingCursor cursor) {
        return jpaRepository.save(PollingCursorJpaEntity.from(cursor)).toDomain();
    }

    @Override
    public Optional<PollingCursor> findByChannelAndCursorType(String channel, String cursorType) {
        return jpaRepository.findByChannelAndCursorType(channel, cursorType)
                .map(PollingCursorJpaEntity::toDomain);
    }
}
