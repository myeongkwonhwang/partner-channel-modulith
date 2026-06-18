package io.github.orange2652.partner.channel.shared.idempotency;

import java.time.LocalDateTime;
import java.util.Objects;

/**
 * Consumer 멱등성 (R3) — 동일 {@code (consumerName, eventId)} 두 번 처리 방지.
 *
 * <p>{@code (consumerName, eventId)} 가 복합 PK. 이벤트 처리(상태 변경)와 같은 로컬 Tx 안에서 원자
 * INSERT 하면 at-least-once 환경에서도 도메인 변경은 한 번만 적용된다 (Microservices Patterns Ch.3.3.2).</p>
 *
 * <p><b>위치</b>: 도메인 record 와 Port 추상은 shared 에, 물리 테이블(@Entity)은 각 모듈의 자기 schema
 * (saga/core/channel) 에 1벌씩 둔다 (R3 D1). 같은 구조 3벌은 중복이 아니라 "서로 다른 3개 물리 테이블"의
 * 정직한 표현이다.</p>
 *
 * @param consumerName 처리 주체 식별자 (예: {@code "sagaStart"}, {@code "channelConfirm"}, {@code "compensate:unconfirmedOrder"})
 * @param eventId      이벤트 식별자 — 본 프로젝트는 {@code "{channel}:{externalOrderProductId}"} ({@link EventKey})
 * @param processedAt  처리 완료 시각
 */
public record ProcessedEvent(
        String consumerName,
        String eventId,
        LocalDateTime processedAt
) {
    public ProcessedEvent {
        Objects.requireNonNull(consumerName, "consumerName");
        Objects.requireNonNull(eventId, "eventId");
        Objects.requireNonNull(processedAt, "processedAt");
    }

    /** 처리 완료 row — {@code processedAt = now}. */
    public static ProcessedEvent newRecord(String consumerName, String eventId) {
        return new ProcessedEvent(consumerName, eventId, LocalDateTime.now());
    }
}
