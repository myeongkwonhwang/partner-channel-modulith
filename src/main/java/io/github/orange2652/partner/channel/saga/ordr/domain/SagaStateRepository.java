package io.github.orange2652.partner.channel.saga.ordr.domain;

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
}
