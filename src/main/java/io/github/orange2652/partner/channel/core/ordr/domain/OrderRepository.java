package io.github.orange2652.partner.channel.core.ordr.domain;

import java.util.Optional;

/**
 * 내부 확정 주문 영속 Port — A1 step4 Pivot.
 *
 * <p>{@code (channel, externalOrderProductId)} UNIQUE — 중복 INSERT 시
 * {@link org.springframework.dao.DataIntegrityViolationException}. 호출자가 멱등성 결정.
 * {@link #findByChannelAndExternalOrderProductId} 는 "A1 통과 여부 확인" 용(존재=완전 통과).</p>
 */
public interface OrderRepository {

    Order save(Order order);

    Optional<Order> findByChannelAndExternalOrderProductId(String channel, String externalOrderProductId);
}
