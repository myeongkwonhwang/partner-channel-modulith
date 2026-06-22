package io.github.orange2652.partner.channel.saga.ordr.infra.idempotency;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.orange2652.partner.channel.saga.ordr.infra.PersistenceSliceTest;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * {@link SagaProcessedEventRepositoryAdapter} 멱등 가드 슬라이스 테스트 — 본 프로젝트 최우선 불변식(R3).
 *
 * <p>검증: ① 첫 호출 {@code true}/중복 {@code false}, ② 서로 다른 키 독립, ③ 같은 eventId 라도 consumer 가
 * 다르면 독립(saga 진입 vs step 가드), ④ <b>동시성 race</b> — N 스레드가 같은 키로 동시에 {@code markIfFirst}
 * 호출 시 native {@code INSERT ... ON CONFLICT DO NOTHING} 의 원자성으로 정확히 1건만 {@code true}.
 * saga 진입(sagaStart) 멱등은 at-least-once 트리거에서 saga 인스턴스 중복 생성을 막는 1차 방어다.</p>
 *
 * <p><b>커밋 Tx 주의</b>: {@code @DataJpaTest} 의 메서드 롤백 Tx 를 끈다({@code NOT_SUPPORTED}). {@code markIfFirst}
 * 를 {@code TransactionTemplate(REQUIRES_NEW)} 로 독립 커밋시키고 {@code @BeforeEach} 에서 TRUNCATE 한다.
 * ambient Tx 를 켜두면 그 Tx 의 TRUNCATE(ACCESS EXCLUSIVE 락)가 REQUIRES_NEW 의 INSERT 와 자기-교착에 빠진다
 * (batch/adapter 동일 함정). 검증도 native {@code JdbcTemplate} 로 커밋된 row 를 직접 센다.</p>
 *
 * <p><b>실행 전제: Docker 필요</b> — {@link PersistenceSliceTest} 컨테이너.</p>
 */
@Import(SagaProcessedEventRepositoryAdapter.class)
@Transactional(propagation = Propagation.NOT_SUPPORTED)
class SagaProcessedEventRepositoryAdapterTest extends PersistenceSliceTest {

    private static final String CONSUMER = "sagaStart";
    private static final String EVENT_ID = "TOSS:o-1";

    @Autowired
    private SagaProcessedEventRepositoryAdapter adapter;

    @Autowired
    private PlatformTransactionManager txManager;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @BeforeEach
    void cleanProcessedEvent() {
        jdbcTemplate.execute("TRUNCATE TABLE saga_schema.processed_event");
    }

    private boolean markIfFirstCommitted(String consumerName, String eventId) {
        TransactionTemplate tx = new TransactionTemplate(txManager);
        tx.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
        return Boolean.TRUE.equals(tx.execute(status -> adapter.markIfFirst(consumerName, eventId)));
    }

    private long countRows(String consumerName, String eventId) {
        Long count = jdbcTemplate.queryForObject(
                "SELECT count(*) FROM saga_schema.processed_event WHERE consumer_name = ? AND event_id = ?",
                Long.class, consumerName, eventId);
        return count == null ? 0L : count;
    }

    @Test
    void 첫_호출은_true_이고_중복은_false() {
        boolean first = markIfFirstCommitted(CONSUMER, EVENT_ID);
        boolean second = markIfFirstCommitted(CONSUMER, EVENT_ID);

        assertThat(first).isTrue();
        assertThat(second).isFalse();
        assertThat(countRows(CONSUMER, EVENT_ID)).isEqualTo(1L);
    }

    @Test
    void 서로_다른_eventId_는_독립적으로_모두_true() {
        boolean a = markIfFirstCommitted(CONSUMER, "TOSS:o-1");
        boolean b = markIfFirstCommitted(CONSUMER, "TOSS:o-2");

        assertThat(a).isTrue();
        assertThat(b).isTrue();
    }

    @Test
    void 같은_eventId_라도_consumerName_이_다르면_독립적으로_모두_true() {
        // 복합 PK (consumer_name, event_id) — saga 진입과 step 가드는 같은 주문이라도 별개
        boolean start = markIfFirstCommitted("sagaStart", EVENT_ID);
        boolean confirm = markIfFirstCommitted("channelConfirmReply", EVENT_ID);

        assertThat(start).isTrue();
        assertThat(confirm).isTrue();
    }

    @Test
    void 동시에_같은_키로_호출해도_정확히_1건만_true() throws InterruptedException {
        // given — N 스레드가 동시 출발
        int n = 20;
        ExecutorService pool = Executors.newFixedThreadPool(n);
        CountDownLatch start = new CountDownLatch(1);
        CountDownLatch done = new CountDownLatch(n);
        AtomicInteger trueCount = new AtomicInteger();

        try {
            for (int i = 0; i < n; i++) {
                pool.submit(() -> {
                    try {
                        start.await();
                        if (markIfFirstCommitted(CONSUMER, "TOSS:race-1")) {
                            trueCount.incrementAndGet();
                        }
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                    } finally {
                        done.countDown();
                    }
                    return null;
                });
            }

            // when — 동시 출발
            start.countDown();
            boolean finished = done.await(30, TimeUnit.SECONDS);

            // then — ON CONFLICT DO NOTHING 원자성: 정확히 1건만 신규, DB row 도 1건
            assertThat(finished).isTrue();
            assertThat(trueCount.get()).isEqualTo(1);
            assertThat(countRows(CONSUMER, "TOSS:race-1")).isEqualTo(1L);
        } finally {
            pool.shutdownNow();
        }
    }
}
