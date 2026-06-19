package io.github.orange2652.partner.channel.batch.ordr.application;

import io.github.orange2652.partner.channel.shared.domain.Channel;
import java.time.Instant;
import java.util.Objects;

/**
 * 채널 polling 으로 수집한 외부 주문 1건 (raw) — {@link PollingStrategy} 반환 단위.
 *
 * <p>아직 30분 수신 지연(R1)·멱등(R3) 가드 이전의 원시 결과다. 가드 통과분만 이후 {@code OrderReceivedEvent}
 * 로 변환되어 발행된다. 시각은 외부 채널값이라 DB({@code LocalDateTime})가 아닌 {@link Instant} 경계를 쓴다
 * ({@code OrderReceivedEvent.orderedAt} 정렬).</p>
 *
 * @param channel                채널
 * @param externalOrderProductId 외부 주문상품 식별자 — 멱등 키의 일부
 * @param orderedAt              외부 채널 주문 시점 (R1 30분 대기 기준점)
 * @param rawPayload             채널 응답 원본(JSON)
 */
public record PolledOrder(
        Channel channel,
        String externalOrderProductId,
        Instant orderedAt,
        String rawPayload
) {
    public PolledOrder {
        Objects.requireNonNull(channel, "channel");
        Objects.requireNonNull(externalOrderProductId, "externalOrderProductId");
        Objects.requireNonNull(orderedAt, "orderedAt");
        Objects.requireNonNull(rawPayload, "rawPayload");
    }
}
