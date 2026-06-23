package io.github.orange2652.partner.channel.saga.ordr.domain;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * saga 인스턴스 상태 영속 Port — saga_schema 로컬.
 *
 * <p>{@code sagaId} UNIQUE — 중복 INSERT 시 {@link org.springframework.dao.DataIntegrityViolationException}.
 * {@link #findBySagaId} 는 reply 수신 시 인스턴스 로드(advance) 용, {@link #findByCorrelationKey} 는
 * "이 주문에 이미 saga 가 있는가" 확인(saga 진입 보조 점검 — 1차 멱등은 R3 {@code processed_event}). 구현은 infra.</p>
 */
public interface SagaStateRepository {

    SagaState save(SagaState sagaState);

    Optional<SagaState> findBySagaId(UUID sagaId);

    Optional<SagaState> findByCorrelationKey(String correlationKey);

    /**
     * 주어진 status 이면서 마지막 전이가 {@code threshold} 이전인(=그 이후로 멈춰 있는) saga 들 — R5a stuck scanner 용.
     * saga 자기 schema 만 조회한다.
     */
    List<SagaState> findStuck(String status, LocalDateTime threshold);
}
