package io.github.orange2652.partner.channel.shared.event.ordr;

import java.time.Instant;
import java.util.Objects;
import org.springframework.modulith.events.Externalized;

/**
 * A1 트리거 (R1) — 외부 채널 polling 이 30분 경과분만 발행하는 주문 수신 이벤트.
 *
 * <p>{@code @Externalized} 로 외부 Kafka 토픽 {@code channel.order.received} 에 발행되며(외부 알림/MSA 호환),
 * 동일 이벤트가 in-VM 으로 saga 모듈의 sagaStart listener 에 전달되어 A1 saga 를 시작한다. saga 는 Kafka 를
 * 되읽지 않는다 (self-consume 회피 — R2 Q2).</p>
 *
 * @param channel                채널 코드 ({@code TOSS/NAVER/COUPANG})
 * @param externalOrderProductId 외부 주문상품 식별자 — 멱등 키의 일부
 * @param orderedAt              외부 채널 주문 시점 (30분 대기 기준점)
 * @param receivedAt             자사 polling 수신 시각
 * @param rawPayload             채널 응답 원본(JSON) — 이후 step 들이 파싱
 */
@Externalized("channel.order.received")
public record OrderReceivedEvent(
        String channel,
        String externalOrderProductId,
        Instant orderedAt,
        Instant receivedAt,
        String rawPayload
) {
    public OrderReceivedEvent {
        Objects.requireNonNull(channel, "channel");
        Objects.requireNonNull(externalOrderProductId, "externalOrderProductId");
        Objects.requireNonNull(orderedAt, "orderedAt");
        Objects.requireNonNull(receivedAt, "receivedAt");
        Objects.requireNonNull(rawPayload, "rawPayload");
    }
}
