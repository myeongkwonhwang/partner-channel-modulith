package io.github.orange2652.partner.channel.shared.idempotency;

/**
 * Consumer 멱등성 Port (R3) — 동일 {@code (consumerName, eventId)} 두 번 처리 방지.
 *
 * <p>구현체(각 모듈 infra)는 native {@code INSERT ... ON CONFLICT (consumer_name, event_id) DO NOTHING}
 * + 영향 행 수 판정으로 <b>원자적</b> 가드를 제공한다. JPA {@code save()}(select-then-insert)는 동시성
 * 하에서 race 가 남으므로 쓰지 않는다 (R3 / java-spring-expert 자문).</p>
 *
 * <p><b>호출 규약</b>: 가드 INSERT 는 비즈니스 DB 쓰기와 <b>같은 로컬 Tx</b> 안에서 실행한다. 외부 API
 * 호출은 그 Tx 밖. (협업원칙 — 외부+DB 분리)</p>
 */
public interface ProcessedEventRepository {

    /**
     * {@code (consumerName, eventId)} 를 원자적으로 마킹 시도한다.
     *
     * @return 신규로 마킹됐으면 {@code true} (이번이 처음 — 처리 진행). 이미 존재하면 {@code false}
     *         (중복 — 처리 skip).
     */
    boolean markIfFirst(String consumerName, String eventId);
}
