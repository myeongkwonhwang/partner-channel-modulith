package io.github.orange2652.partner.channel.shared.event.ordr;

import java.util.Objects;
import java.util.UUID;

/**
 * A1 step2 — validate command (판매가능 검증 요청). saga 발행 → core 수신.
 *
 * <p><b>read-only step</b>: core 는 DB 를 쓰지 않는다. 정규화 필드(예: 토스 {@code orderProductStatus})
 * 만 보고 채널별 룰 적용. R3: read-only 라 멱등 가드 생략.</p>
 *
 * @param sagaId  상관 키
 * @param channel 채널 코드 — 채널별 룰 분기
 * @param raw     채널 응답 원본(JSON)
 */
public record ValidateCommand(
        UUID sagaId,
        String channel,
        String raw
) {
    public ValidateCommand {
        Objects.requireNonNull(sagaId, "sagaId");
        Objects.requireNonNull(channel, "channel");
        Objects.requireNonNull(raw, "raw");
    }
}
