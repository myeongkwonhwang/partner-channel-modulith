package io.github.orange2652.partner.channel.adapter.ordr.application;

/**
 * adapter 모듈 멱등 가드 (R3) — 동일 {@code (consumerName, eventId)} 두 번 처리 방지. 모듈-로컬 outbound port.
 *
 * <p>구현(infra)은 native {@code INSERT ... ON CONFLICT DO NOTHING} + 영향 행 수 판정으로 원자 가드. 가드 INSERT 는
 * step1/step3/보상 흐름의 비즈니스 DB 쓰기(staging_order)와 같은 로컬 Tx, 외부 channel API 호출은 Tx 밖.
 * R3/D-3 조정으로 shared 단일 Port 를 모듈별 전용 Port 로 분리 — 자세한 근거는 saga 의
 * {@code SagaIdempotencyGuard} javadoc 참조.</p>
 */
public interface AdapterIdempotencyGuard {

    boolean markIfFirst(String consumerName, String eventId);
}
