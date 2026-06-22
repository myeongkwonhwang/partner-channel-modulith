package io.github.orange2652.partner.channel.core.ordr.application;

/**
 * core 모듈 멱등 가드 (R3) — 동일 {@code (consumerName, eventId)} 두 번 처리 방지. 모듈-로컬 outbound port.
 *
 * <p>구현(infra)은 native {@code INSERT ... ON CONFLICT DO NOTHING} + 영향 행 수 판정으로 원자 가드. 가드 INSERT 는
 * 비즈니스 DB 쓰기(orders)와 같은 로컬 Tx, 외부 호출은 Tx 밖. R3/D-3 조정으로 shared 단일 Port 를 모듈별 전용
 * Port 로 분리(구현 4개 주입 모호성 제거 + 모듈이 자기 outbound port 소유) — 자세한 근거는 saga 의
 * {@code SagaIdempotencyGuard} javadoc 참조.</p>
 */
public interface CoreIdempotencyGuard {

    boolean markIfFirst(String consumerName, String eventId);
}
