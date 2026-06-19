package io.github.orange2652.partner.channel.batch.ordr.application;

import io.github.orange2652.partner.channel.shared.domain.Channel;
import java.time.Instant;
import java.util.List;

/**
 * 채널별 주문 polling 전략 Port (R5b D-1) — batch 책임.
 *
 * <p>채널마다 polling 규격이 다르다 (토스=일 단위/옵션 B, 네이버·쿠팡=분·시 단위/옵션 D). 디스패치는
 * 이후 {@code Map<Channel, PollingStrategy>} 빈 자동수집 Registry(R5b D-2, OCP·매직스트링 금지)로 한다.
 * R1 의 30분 수신 지연 가드는 본 전략 <b>바깥</b>(polling application 흐름)에서 적용한다 — 전략은 원시 수집만.</p>
 *
 * <p>구현체(채널별)는 미확정 spec 격리를 위해 Phase 2 이연(R5b D-5). Phase 1 은 Port 골격만 둔다.</p>
 */
public interface PollingStrategy {

    /** 디스패치 키 — Registry 가 본 값으로 채널 라우팅한다. */
    Channel channel();

    /**
     * {@code since} 이후 외부 주문을 수집한다. cursor 윈도 의미(일/분·시)는 채널 구현이 해석한다.
     *
     * @param since polling 윈도 시작점 (cursor {@code lastPolledAt})
     * @return 수집된 원시 주문 목록 (가드 이전)
     */
    List<PolledOrder> pollSince(Instant since);
}
