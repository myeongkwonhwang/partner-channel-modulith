package io.github.orange2652.partner.channel.adapter.ordr.domain;

import java.util.Optional;

/**
 * 미확정 적재(staging) 영속 Port — channel_schema 로컬. A1 step1 INSERT + R4 보상(CANCELED 전이).
 *
 * <p>{@code (channel, externalOrderProductId)} UNIQUE — 중복 INSERT 시
 * {@link org.springframework.dao.DataIntegrityViolationException}. 호출자가 멱등성 결정
 * ({@link #findByChannelAndExternalOrderProductId} 선조회 또는 R3 {@code processed_event} 가드).
 * R4 보상(staging CANCELED 전이)도 {@code (channel, externalOrderProductId)} 자연키로 대상 row 를 찾는다
 * (saga 가 correlationKey 로 보유 — surrogate stagingId 영속 불필요). {@link #findById} 는 surrogate PK 직접
 * 조회용 보조 capability. 구현은 infra.</p>
 */
public interface StagingOrderRepository {

    StagingOrder save(StagingOrder stagingOrder);

    Optional<StagingOrder> findById(Long id);

    Optional<StagingOrder> findByChannelAndExternalOrderProductId(String channel, String externalOrderProductId);
}
