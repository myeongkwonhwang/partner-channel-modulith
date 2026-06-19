package io.github.orange2652.partner.channel.batch.ordr.infra.idempotency;

import io.github.orange2652.partner.channel.shared.idempotency.ProcessedEventRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * batch 모듈 멱등 가드 구현 (R3). native {@code ON CONFLICT} 의 영향 행 수로 신규/중복 판정.
 *
 * <p>호출자(polling 흐름)의 비즈니스 DB 쓰기와 같은 로컬 Tx 안에서 호출돼야 원자성이 성립한다.
 * 외부 channel polling/API 호출은 그 Tx 밖(협업원칙).</p>
 */
@Component
@RequiredArgsConstructor
class ChannelProcessedEventRepositoryAdapter implements ProcessedEventRepository {

    private final ChannelProcessedEventJpaRepository jpaRepository;

    @Override
    public boolean markIfFirst(String consumerName, String eventId) {
        return jpaRepository.insertIfAbsent(consumerName, eventId) == 1;
    }
}
