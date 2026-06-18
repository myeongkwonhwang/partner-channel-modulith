package io.github.orange2652.partner.channel.shared.policy;

import java.time.Duration;

/**
 * 주문 수신 대기 정책 (R1) — {@code orderedAt + RECEPTION_DELAY <= now} 인 항목만 수신(publish) 대상.
 *
 * <p>채널 측 즉시 취소(주문 직후 변심/결제 사후 처리)가 SAGA 시작 직후 보상을 유발하는 비용을 회피하고,
 * 외부 채널 데이터(주문 항목/결제 상태)가 안정화될 시간을 확보하기 위함. 미경과 항목은 state 미보유로
 * skip 되어 다음 polling 주기에 재평가된다.</p>
 *
 * <p><b>R5a 확장</b>: 본 상수는 fallback 기본값. 운영 단계에서 {@code @ConfigurationProperties}
 * (batch 모듈) 의 채널별 override 로 대체된다 — 상수 자체는 supersede 가 아니라 기본값으로 잔존.</p>
 */
public final class ReceptionDelay {

    /** 수신 대기 기본값 — 30분. */
    public static final Duration RECEPTION_DELAY = Duration.ofMinutes(30);

    private ReceptionDelay() {
    }
}
