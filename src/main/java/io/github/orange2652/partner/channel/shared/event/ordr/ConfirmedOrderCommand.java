package io.github.orange2652.partner.channel.shared.event.ordr;

import java.util.Objects;
import java.util.UUID;

/**
 * A1 step4 — confirmedOrder command (내부 주문 확정 + 외부 물류 전송 요청). saga 발행 → core 수신.
 *
 * <p><b>Pivot ★</b>: core 가 ① 자사 물류 호출(Tx 밖) → ② 내부 주문 INSERT(Tx 안). INSERT 성공 = Pivot 통과
 * 시점이라 이후 자동 보상 불가. 부분 실패(외부 OK + INSERT 실패)는 reconciliation 으로 분기 (R4/ADR-0004).</p>
 *
 * @param sagaId  상관 키
 * @param channel 채널 코드
 * @param raw     채널 응답 원본(JSON)
 */
public record ConfirmedOrderCommand(
        UUID sagaId,
        String channel,
        String raw
) {
    public ConfirmedOrderCommand {
        Objects.requireNonNull(sagaId, "sagaId");
        Objects.requireNonNull(channel, "channel");
        Objects.requireNonNull(raw, "raw");
    }
}
