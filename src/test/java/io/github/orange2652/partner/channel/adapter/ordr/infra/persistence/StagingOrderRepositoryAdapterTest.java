package io.github.orange2652.partner.channel.adapter.ordr.infra.persistence;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.github.orange2652.partner.channel.adapter.ordr.domain.StagingOrder;
import io.github.orange2652.partner.channel.adapter.ordr.infra.PersistenceSliceTest;
import jakarta.persistence.EntityManager;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * {@link StagingOrderRepositoryAdapter} infra 슬라이스 테스트 — 실제 PostgreSQL(Testcontainers).
 *
 * <p>검증: ① mapper 왕복(domain → @Entity → save → toDomain, id 채번), ② {@code @Table(schema)} 라우팅
 * (row 가 {@code channel_schema.staging_order} 에 적재), ③ {@code raw} 의 {@code jsonb} 매핑
 * ({@code @JdbcTypeCode(JSON)} — 의미 동등 비교), ④ {@code (channel, external_order_product_id)} UNIQUE 위반,
 * ⑤ {@code findById} / {@code findByChannelAndExternalOrderProductId} 존재·부재, ⑥ CANCELED 전이 UPDATE 반영.</p>
 *
 * <p>패키지를 프로덕션과 동일하게 둬 package-private Adapter/Entity 에 접근한다. {@code @DataJpaTest} 슬라이스는
 * {@code @Component} 를 스캔하지 않으므로 Adapter 만 {@code @Import} 한다.</p>
 *
 * <p><b>실행 전제: Docker 필요</b> — {@link PersistenceSliceTest} 컨테이너.</p>
 */
@Import(StagingOrderRepositoryAdapter.class)
class StagingOrderRepositoryAdapterTest extends PersistenceSliceTest {

    private static final String CHANNEL = "TOSS";
    private static final String EXT_ID = "o-1";
    private static final String RAW = "{\"orderProductId\":\"o-1\",\"status\":\"PAID\"}";

    @Autowired
    private StagingOrderRepositoryAdapter adapter;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private EntityManager entityManager;

    @Test
    void save_는_id_를_채번하고_왕복_매핑한다() {
        // given
        StagingOrder staging = StagingOrder.newRecord(CHANNEL, EXT_ID, RAW);

        // when
        StagingOrder saved = adapter.save(staging);

        // then — id 채번 + 필드 보존
        assertThat(saved.id()).isNotNull();
        assertThat(saved.channel()).isEqualTo(CHANNEL);
        assertThat(saved.externalOrderProductId()).isEqualTo(EXT_ID);
        assertThat(saved.status()).isEqualTo("ACTIVE");
    }

    @Test
    void save_된_row_는_channel_schema_에_라우팅된다() {
        // given
        adapter.save(StagingOrder.newRecord(CHANNEL, EXT_ID, RAW));

        // when — @Table(schema="channel_schema") 가 실제 매핑됐는지 native count 로 직접 확인
        Long count = jdbcTemplate.queryForObject(
                "SELECT count(*) FROM channel_schema.staging_order WHERE channel = ? AND external_order_product_id = ?",
                Long.class, CHANNEL, EXT_ID);

        // then
        assertThat(count).isEqualTo(1L);
    }

    @Test
    void raw_는_jsonb_로_저장되어_의미가_동등하다() {
        // given
        adapter.save(StagingOrder.newRecord(CHANNEL, EXT_ID, RAW));

        // when — jsonb = jsonb 비교는 공백/키순서 무관(의미 동등). 매핑이 text 였다면 캐스팅·비교가 깨진다.
        Long count = jdbcTemplate.queryForObject(
                "SELECT count(*) FROM channel_schema.staging_order WHERE raw = ?::jsonb",
                Long.class, RAW);

        // then
        assertThat(count).isEqualTo(1L);
    }

    @Test
    void 같은_channel_ext_중복_save_는_UNIQUE_위반() {
        // given — 첫 staging 저장
        adapter.save(StagingOrder.newRecord(CHANNEL, EXT_ID, RAW));

        // when / then — uk_staging_order_channel_ext (channel, external_order_product_id) 위반
        assertThatThrownBy(() -> adapter.save(StagingOrder.newRecord(CHANNEL, EXT_ID, RAW)))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void findById_는_존재하면_도메인을_반환하고_부재하면_empty() {
        // given
        StagingOrder saved = adapter.save(StagingOrder.newRecord(CHANNEL, EXT_ID, RAW));

        // when / then — 존재
        Optional<StagingOrder> found = adapter.findById(saved.id());
        assertThat(found).isPresent();
        assertThat(found.get().externalOrderProductId()).isEqualTo(EXT_ID);
        assertThat(found.get().raw()).contains("\"orderProductId\"").contains("o-1");

        // when / then — 부재
        assertThat(adapter.findById(saved.id() + 999L)).isEmpty();
    }

    @Test
    void findByChannelAndExternalOrderProductId_은_존재_부재_양쪽() {
        // given
        adapter.save(StagingOrder.newRecord(CHANNEL, EXT_ID, RAW));

        // when / then
        assertThat(adapter.findByChannelAndExternalOrderProductId(CHANNEL, EXT_ID)).isPresent();
        assertThat(adapter.findByChannelAndExternalOrderProductId("NAVER", EXT_ID)).isEmpty();
    }

    @Test
    void canceled_전이_save_는_같은_row_의_status_를_CANCELED_로_UPDATE_한다() {
        // given — ACTIVE 저장
        StagingOrder saved = adapter.save(StagingOrder.newRecord(CHANNEL, EXT_ID, RAW));

        // when — 같은 id 로 CANCELED 전이 저장(merge → UPDATE)
        adapter.save(saved.canceled());
        entityManager.flush();   // 지연된 merge UPDATE 를 DB 로 강제 — flush 없이는 native read 가 옛 값(ACTIVE)을 본다

        // then — UPDATE 가 실제 DB 에 반영됐는지 native 로 직접 확인(영속 컨텍스트 L1 우회). 행 수는 그대로 1.
        Long total = jdbcTemplate.queryForObject(
                "SELECT count(*) FROM channel_schema.staging_order WHERE id = ?", Long.class, saved.id());
        String status = jdbcTemplate.queryForObject(
                "SELECT status FROM channel_schema.staging_order WHERE id = ?", String.class, saved.id());
        assertThat(total).isEqualTo(1L);
        assertThat(status).isEqualTo("CANCELED");
    }
}
