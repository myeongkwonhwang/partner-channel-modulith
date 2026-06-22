package io.github.orange2652.partner.channel.saga.ordr.infra.persistence;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.github.orange2652.partner.channel.saga.ordr.domain.SagaState;
import io.github.orange2652.partner.channel.saga.ordr.infra.PersistenceSliceTest;
import jakarta.persistence.EntityManager;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * {@link SagaStateRepositoryAdapter} infra 슬라이스 테스트 — 실제 PostgreSQL(Testcontainers).
 *
 * <p>검증: ① mapper 왕복(domain → @Entity → save → toDomain, id 채번), ② {@code @Table(schema)} 라우팅
 * (row 가 {@code saga_schema.saga_state} 에 적재), ③ {@code payload} 의 {@code jsonb} 매핑(의미 동등),
 * ④ {@code saga_id} UNIQUE 위반, ⑤ {@code findBySagaId} / {@code findByCorrelationKey} 존재·부재,
 * ⑥ {@code advanceTo} 전이 UPDATE 반영(merge → Port 왕복으로 확인).</p>
 *
 * <p>패키지를 프로덕션과 동일하게 둬 package-private Adapter/Entity 에 접근한다. {@code @DataJpaTest} 슬라이스는
 * {@code @Component} 를 스캔하지 않으므로 Adapter 만 {@code @Import} 한다.</p>
 *
 * <p><b>실행 전제: Docker 필요</b> — {@link PersistenceSliceTest} 컨테이너.</p>
 */
@Import(SagaStateRepositoryAdapter.class)
class SagaStateRepositoryAdapterTest extends PersistenceSliceTest {

    private static final UUID SAGA_ID = UUID.fromString("00000000-0000-0000-0000-0000000000aa");
    private static final String TYPE = "A1_ORDER";
    private static final String CORRELATION = "TOSS:o-1";
    private static final String STEP = "UNCONFIRMED_ORDER";
    private static final String PAYLOAD = "{\"channel\":\"TOSS\",\"externalOrderProductId\":\"o-1\"}";

    @Autowired
    private SagaStateRepositoryAdapter adapter;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private EntityManager entityManager;

    @Test
    void save_는_id_를_채번하고_왕복_매핑한다() {
        // when
        SagaState saved = adapter.save(SagaState.start(SAGA_ID, TYPE, CORRELATION, STEP, PAYLOAD));

        // then — id 채번 + 필드 보존
        assertThat(saved.id()).isNotNull();
        assertThat(saved.sagaId()).isEqualTo(SAGA_ID);
        assertThat(saved.sagaType()).isEqualTo(TYPE);
        assertThat(saved.correlationKey()).isEqualTo(CORRELATION);
        assertThat(saved.currentStep()).isEqualTo(STEP);
        assertThat(saved.status()).isEqualTo("RUNNING");
        assertThat(saved.reconciliationAttempts()).isZero();
    }

    @Test
    void save_된_row_는_saga_schema_에_라우팅된다() {
        // given
        adapter.save(SagaState.start(SAGA_ID, TYPE, CORRELATION, STEP, PAYLOAD));

        // when — @Table(schema="saga_schema") 가 실제 매핑됐는지 native count 로 직접 확인
        Long count = jdbcTemplate.queryForObject(
                "SELECT count(*) FROM saga_schema.saga_state WHERE saga_id = ?", Long.class, SAGA_ID);

        // then
        assertThat(count).isEqualTo(1L);
    }

    @Test
    void payload_는_jsonb_로_저장되어_의미가_동등하다() {
        // given
        adapter.save(SagaState.start(SAGA_ID, TYPE, CORRELATION, STEP, PAYLOAD));

        // when — jsonb = jsonb 비교는 공백/키순서 무관(의미 동등). 매핑이 text 였다면 캐스팅·비교가 깨진다.
        Long count = jdbcTemplate.queryForObject(
                "SELECT count(*) FROM saga_schema.saga_state WHERE payload = ?::jsonb", Long.class, PAYLOAD);

        // then
        assertThat(count).isEqualTo(1L);
    }

    @Test
    void 같은_sagaId_중복_save_는_UNIQUE_위반() {
        // given — 첫 saga 저장
        adapter.save(SagaState.start(SAGA_ID, TYPE, CORRELATION, STEP, PAYLOAD));

        // when / then — saga_id UNIQUE 위반 (correlationKey 가 달라도 sagaId 중복이면 거부)
        assertThatThrownBy(() -> adapter.save(SagaState.start(SAGA_ID, TYPE, "TOSS:o-2", STEP, PAYLOAD)))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void findBySagaId_는_존재_부재_양쪽() {
        // given
        adapter.save(SagaState.start(SAGA_ID, TYPE, CORRELATION, STEP, PAYLOAD));

        // when / then
        assertThat(adapter.findBySagaId(SAGA_ID)).isPresent();
        assertThat(adapter.findBySagaId(UUID.fromString("00000000-0000-0000-0000-0000000000ff"))).isEmpty();
    }

    @Test
    void findByCorrelationKey_는_존재_부재_양쪽() {
        // given
        adapter.save(SagaState.start(SAGA_ID, TYPE, CORRELATION, STEP, PAYLOAD));

        // when / then
        assertThat(adapter.findByCorrelationKey(CORRELATION)).isPresent();
        assertThat(adapter.findByCorrelationKey("NAVER:x-9")).isEmpty();
    }

    @Test
    void advanceTo_전이_save_는_같은_row_의_currentStep_을_UPDATE_한다() {
        // given — UNCONFIRMED_ORDER 저장
        SagaState saved = adapter.save(SagaState.start(SAGA_ID, TYPE, CORRELATION, STEP, PAYLOAD));

        // when — 같은 id 로 VALIDATE 전이 저장(merge → UPDATE)
        adapter.save(saved.advanceTo("VALIDATE"));
        entityManager.flush();   // 지연된 merge UPDATE 를 DB 로 강제 — flush 없이는 native read 가 옛 step 을 본다

        // then — UPDATE 가 실제 DB 에 반영됐는지 native 로 직접 확인(영속 컨텍스트 L1 우회). 행 수 그대로 1.
        Long total = jdbcTemplate.queryForObject(
                "SELECT count(*) FROM saga_schema.saga_state WHERE saga_id = ?", Long.class, SAGA_ID);
        String currentStep = jdbcTemplate.queryForObject(
                "SELECT current_step FROM saga_schema.saga_state WHERE saga_id = ?", String.class, SAGA_ID);
        assertThat(total).isEqualTo(1L);
        assertThat(currentStep).isEqualTo("VALIDATE");
    }
}
