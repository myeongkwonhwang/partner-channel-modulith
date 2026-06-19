package io.github.orange2652.partner.channel.batch.ordr.domain;

import java.time.LocalDateTime;
import java.util.Objects;

/**
 * 채널별 polling 진행 위치 — {@code channel_schema.polling_cursor} 매핑.
 *
 * <p>{@code (channel, cursorType)} UNIQUE — 채널·스트림(주문/송장) 1벌. {@code lastPolledAt} 은 다음 polling
 * 윈도 시작점. R1 의 30분 수신 지연 가드는 cursor 가 아니라 polling application 흐름(이후 Phase)에서 적용한다.</p>
 *
 * @param id           DB PK (신규는 null)
 * @param channel      채널 코드
 * @param cursorType   polling 스트림 구분 (주문/송장 등) — DB 저장값
 * @param lastPolledAt 마지막 polling 기준 시각 (다음 윈도 시작점)
 * @param updatedAt    마지막 갱신 시각
 */
public record PollingCursor(
        Long id,
        String channel,
        String cursorType,
        LocalDateTime lastPolledAt,
        LocalDateTime updatedAt
) {
    public PollingCursor {
        Objects.requireNonNull(channel, "channel");
        Objects.requireNonNull(cursorType, "cursorType");
        Objects.requireNonNull(lastPolledAt, "lastPolledAt");
    }

    /** 채널·스트림 첫 cursor — {@code id=null}, {@code updatedAt=now}. */
    public static PollingCursor initial(String channel, String cursorType, LocalDateTime lastPolledAt) {
        return new PollingCursor(null, channel, cursorType, lastPolledAt, LocalDateTime.now());
    }

    /** 다음 기준 시각으로 전진한 새 cursor(불변) — {@code updatedAt=now}. */
    public PollingCursor advanceTo(LocalDateTime nextLastPolledAt) {
        Objects.requireNonNull(nextLastPolledAt, "nextLastPolledAt");
        return new PollingCursor(id, channel, cursorType, nextLastPolledAt, LocalDateTime.now());
    }
}
