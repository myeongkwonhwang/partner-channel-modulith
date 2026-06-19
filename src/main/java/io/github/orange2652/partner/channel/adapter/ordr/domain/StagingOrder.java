package io.github.orange2652.partner.channel.adapter.ordr.domain;

import java.time.LocalDateTime;
import java.util.Objects;

/**
 * 외부 채널 raw 의 미확정 적재 — {@code channel_schema.staging_order} 매핑. A1 step1(unconfirmedOrder) INSERT.
 *
 * <p>{@code (channel, externalOrderProductId)} UNIQUE — 중복 적재 차단(멱등). R2 분기로 step1 은 staging INSERT
 * 만 한다(외부 PREPARING_PRODUCT 전이는 step3 channelConfirm 으로 분리). 보상(R4)은 DELETE 가 아니라
 * {@code status} 를 CANCELED 로 전이한다 — 이미 CANCELED 면 NoOp(멱등).</p>
 *
 * @param id                     DB PK (신규는 null)
 * @param channel                채널 코드
 * @param externalOrderProductId 외부 주문상품 식별자 — UNIQUE
 * @param raw                    채널 응답 원본(JSON)
 * @param status                 적재 상태 (신규 {@code "ACTIVE"}, 보상 시 {@code "CANCELED"})
 * @param receivedAt             적재 시각
 * @param updatedAt              마지막 갱신 시각
 */
public record StagingOrder(
        Long id,
        String channel,
        String externalOrderProductId,
        String raw,
        String status,
        LocalDateTime receivedAt,
        LocalDateTime updatedAt
) {
    private static final String STATUS_ACTIVE = "ACTIVE";
    private static final String STATUS_CANCELED = "CANCELED";

    public StagingOrder {
        Objects.requireNonNull(channel, "channel");
        Objects.requireNonNull(externalOrderProductId, "externalOrderProductId");
        Objects.requireNonNull(raw, "raw");
        Objects.requireNonNull(status, "status");
    }

    /** 새 적재 — {@code id=null}, {@code status="ACTIVE"}, 시각은 now. */
    public static StagingOrder newRecord(String channel, String externalOrderProductId, String raw) {
        LocalDateTime now = LocalDateTime.now();
        return new StagingOrder(null, channel, externalOrderProductId, raw, STATUS_ACTIVE, now, now);
    }

    /** 보상(R4) — CANCELED 로 전이한 새 staging(불변). 이미 CANCELED 면 자기 자신 반환(멱등). */
    public StagingOrder canceled() {
        if (STATUS_CANCELED.equals(status)) {
            return this;
        }
        return new StagingOrder(id, channel, externalOrderProductId, raw, STATUS_CANCELED, receivedAt,
                LocalDateTime.now());
    }

    public boolean isCanceled() {
        return STATUS_CANCELED.equals(status);
    }
}
