package io.github.orange2652.partner.channel.saga.ordr.application;

/**
 * saga 모듈 멱등 가드 (R3) — 동일 {@code (consumerName, eventId)} 두 번 처리 방지. 모듈-로컬 outbound port.
 *
 * <p>구현(infra)은 native {@code INSERT ... ON CONFLICT (consumer_name, event_id) DO NOTHING} + 영향 행 수 판정으로
 * <b>원자적</b> 가드를 제공한다(JPA {@code save()} 의 select-then-insert race 회피). 가드 INSERT 는 비즈니스 DB
 * 쓰기(saga_state)와 <b>같은 로컬 Tx</b> 안에서, 외부 호출은 그 Tx 밖(협업원칙).</p>
 *
 * <p><b>R3/D-3 조정 (2026-06-19)</b>: shared 단일 {@code ProcessedEventRepository} 를 모듈별 전용 Port 로 분리.
 * 구현이 4개(core/batch/adapter/saga)라 단일 인터페이스 타입 주입은 {@code NoUniqueBeanDefinitionException} 이 되고,
 * Spring DI 는 Modulith 모듈 단위로 스코프되지 않는다. 모듈마다 자기 타입의 Port 를 두면 주입이 유일해지고
 * (타입 안전, 매직스트링 없는 DI) 모듈이 자기 outbound port 를 소유한다(헥사고날 정석). 물리 테이블/구조는 여전히
 * shared {@code ProcessedEventJpaEntity}(@MappedSuperclass) + 모듈별 {@code @Entity} 로 공유한다.</p>
 */
public interface SagaIdempotencyGuard {

    /**
     * {@code (consumerName, eventId)} 를 원자적으로 마킹 시도한다.
     *
     * @return 신규로 마킹됐으면 {@code true}(이번이 처음 — 처리 진행), 이미 존재하면 {@code false}(중복 — skip).
     */
    boolean markIfFirst(String consumerName, String eventId);
}
