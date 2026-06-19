package io.github.orange2652.partner.channel.batch.ordr.infra.idempotency;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.orange2652.partner.channel.batch.ordr.infra.PersistenceSliceTest;
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
 * {@link ChannelProcessedEventRepositoryAdapter} 멱등 가드 슬라이스 테스트 — 본 프로젝트 최우선 불변식(R3).
 *
 * <p>검증: ① 첫 호출 {@code true}/중복 {@code false}, ② 서로 다른 키 독립, ③ <b>동시성 race</b> — N
 * 스레드가 같은 키로 동시에 {@code markIfFirst} 호출 시 native {@code INSERT ... ON CONFLICT DO NOTHING}
 * 의 원자성으로 정확히 1건만 {@code true}.</p>
 *
 * <p><b>커밋 Tx 주의</b>: {@code @DataJpaTest} 는 각 테스트 메서드를 롤백 Tx 로 감싼다. 그 단일 Tx
 * 안에서 호출하면 (가) 같은 Tx 내 두 번째 INSERT 가 PostgreSQL 에서 즉시 제약 위반으로 깨지거나
 * (나) 동시 스레드들이 같은 커넥션·미커밋 상태를 공유해 race 자체가 재현되지 않는다. 따라서 본 테스트는
 * {@code TransactionTemplate(REQUIRES_NEW)} 로 매 호출을 <b>독립 커밋</b>시키고, 검증도 native
 * {@code JdbcTemplate} 로 커밋된 row 를 직접 센다.</p>
 *
 * <p><b>실행 전제: Docker 필요</b> — {@link PersistenceSliceTest} 컨테이너. Docker 미가동 시 본 클래스는
 * 컨테이너 기동 단계에서 건너뛰어진다(race 는 환경상 이 라운드에서 실행 미검증).</p>
 */
@Import(ChannelProcessedEventRepositoryAdapter.class)
// @DataJpaTest 의 메서드 롤백 Tx 를 끈다(NOT_SUPPORTED). 본 테스트는 markIfFirst 를 REQUIRES_NEW 로 직접
// 커밋하고 @BeforeEach 에서 TRUNCATE 한다. ambient Tx 를 켜두면 그 Tx 의 TRUNCATE(ACCESS EXCLUSIVE 락)가
// 테스트 종료까지 유지되는데, REQUIRES_NEW 가 그 Tx 를 suspend 한 채 다른 커넥션에서 INSERT(RowExclusive)를
// 시도해 같은 테이블 락을 기다리며 자기-교착에 빠진다(lock_timeout 없어 무한 대기). ambient Tx 를 끄면
// TRUNCATE 가 즉시 커밋돼 락을 잡지 않는다.
@Transactional(propagation = Propagation.NOT_SUPPORTED)
class ChannelProcessedEventRepositoryAdapterTest extends PersistenceSliceTest {

    private static final String CONSUMER = "orderReception";
    private static final String EVENT_ID = "TOSS:o-1";

    @Autowired
    private ChannelProcessedEventRepositoryAdapter adapter;

    @Autowired
    private PlatformTransactionManager txManager;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    /**
     * 본 테스트는 {@code REQUIRES_NEW} 로 row 를 <b>커밋</b>한다 — @DataJpaTest 의 메서드 롤백 Tx 는
     * 그 커밋된 row 를 되돌리지 못한다. 따라서 메서드 간 누수를 막기 위해 매번 테이블을 비운다.
     */
    @BeforeEach
    void cleanProcessedEvent() {
        jdbcTemplate.execute("TRUNCATE TABLE channel_schema.processed_event");
    }

    /** 매 호출을 독립 커밋시키는 헬퍼 — @DataJpaTest 롤백 Tx 와 분리(REQUIRES_NEW). */
    private boolean markIfFirstCommitted(String consumerName, String eventId) {
        TransactionTemplate tx = new TransactionTemplate(txManager);
        tx.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
        return Boolean.TRUE.equals(tx.execute(status -> adapter.markIfFirst(consumerName, eventId)));
    }

    private long countRows(String consumerName, String eventId) {
        Long count = jdbcTemplate.queryForObject(
                "SELECT count(*) FROM channel_schema.processed_event WHERE consumer_name = ? AND event_id = ?",
                Long.class, consumerName, eventId);
        return count == null ? 0L : count;
    }

    @Test
    void 첫_호출은_true_이고_중복은_false() {
        // when
        boolean first = markIfFirstCommitted(CONSUMER, EVENT_ID);
        boolean second = markIfFirstCommitted(CONSUMER, EVENT_ID);

        // then — 첫 호출만 신규 마킹, row 는 1건
        assertThat(first).isTrue();
        assertThat(second).isFalse();
        assertThat(countRows(CONSUMER, EVENT_ID)).isEqualTo(1L);
    }

    @Test
    void 서로_다른_eventId_는_독립적으로_모두_true() {
        // when
        boolean a = markIfFirstCommitted(CONSUMER, "TOSS:o-1");
        boolean b = markIfFirstCommitted(CONSUMER, "TOSS:o-2");

        // then
        assertThat(a).isTrue();
        assertThat(b).isTrue();
    }

    @Test
    void 같은_eventId_라도_consumerName_이_다르면_독립적으로_모두_true() {
        // when — 복합 PK (consumer_name, event_id) 이므로 consumer 가 다르면 별개
        boolean reception = markIfFirstCommitted("orderReception", EVENT_ID);
        boolean compensate = markIfFirstCommitted("compensate:unconfirmedOrder", EVENT_ID);

        // then
        assertThat(reception).isTrue();
        assertThat(compensate).isTrue();
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
