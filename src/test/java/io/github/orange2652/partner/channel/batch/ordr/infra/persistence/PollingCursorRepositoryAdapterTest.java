package io.github.orange2652.partner.channel.batch.ordr.infra.persistence;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.github.orange2652.partner.channel.batch.ordr.domain.PollingCursor;
import io.github.orange2652.partner.channel.batch.ordr.infra.PersistenceSliceTest;
import java.time.LocalDateTime;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * {@link PollingCursorRepositoryAdapter} infra 슬라이스 테스트 — 실제 PostgreSQL(Testcontainers).
 *
 * <p>검증: ① mapper 왕복(domain → @Entity → save → toDomain, id 채번), ② {@code @Table(schema)} 라우팅
 * (row 가 {@code channel_schema.polling_cursor} 에 적재), ③ {@code (channel, cursor_type)} UNIQUE 위반,
 * ④ 조회 Port 존재/부재 양쪽.</p>
 *
 * <p>패키지를 프로덕션과 동일하게 둬 package-private Adapter/Entity 에 접근한다. {@code @DataJpaTest}
 * 슬라이스는 {@code @Component} 를 스캔하지 않으므로 Adapter 만 {@code @Import} 한다.</p>
 *
 * <p><b>실행 전제: Docker 필요</b> — {@link PersistenceSliceTest} 컨테이너.</p>
 */
@Import(PollingCursorRepositoryAdapter.class)
class PollingCursorRepositoryAdapterTest extends PersistenceSliceTest {

    private static final String CHANNEL = "TOSS";
    private static final String CURSOR_TYPE = "ORDER";
    private static final LocalDateTime LAST_POLLED_AT = LocalDateTime.of(2026, 6, 19, 10, 0, 0);

    @Autowired
    private PollingCursorRepositoryAdapter adapter;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Test
    void save_는_id_를_채번하고_왕복_매핑한다() {
        // given
        PollingCursor cursor = PollingCursor.initial(CHANNEL, CURSOR_TYPE, LAST_POLLED_AT);

        // when
        PollingCursor saved = adapter.save(cursor);

        // then — id 채번 + 필드 보존
        assertThat(saved.id()).isNotNull();
        assertThat(saved.channel()).isEqualTo(CHANNEL);
        assertThat(saved.cursorType()).isEqualTo(CURSOR_TYPE);
        assertThat(saved.lastPolledAt()).isEqualTo(LAST_POLLED_AT);
        assertThat(saved.updatedAt()).isEqualTo(cursor.updatedAt());
    }

    @Test
    void save_된_row_는_channel_schema_에_라우팅된다() {
        // given
        adapter.save(PollingCursor.initial(CHANNEL, CURSOR_TYPE, LAST_POLLED_AT));

        // when — @Table(schema="channel_schema") 가 실제 매핑됐는지 native count 로 직접 확인
        Long count = jdbcTemplate.queryForObject(
                "SELECT count(*) FROM channel_schema.polling_cursor WHERE channel = ? AND cursor_type = ?",
                Long.class, CHANNEL, CURSOR_TYPE);

        // then
        assertThat(count).isEqualTo(1L);
    }

    @Test
    void 같은_channel_cursorType_중복_save_는_UNIQUE_위반() {
        // given — 첫 cursor 저장
        adapter.save(PollingCursor.initial(CHANNEL, CURSOR_TYPE, LAST_POLLED_AT));

        // when / then — uk_polling_cursor (channel, cursor_type) 위반
        assertThatThrownBy(() -> adapter.save(PollingCursor.initial(CHANNEL, CURSOR_TYPE, LAST_POLLED_AT)))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void findByChannelAndCursorType_은_존재하면_도메인을_반환한다() {
        // given
        PollingCursor saved = adapter.save(PollingCursor.initial(CHANNEL, CURSOR_TYPE, LAST_POLLED_AT));

        // when
        Optional<PollingCursor> found = adapter.findByChannelAndCursorType(CHANNEL, CURSOR_TYPE);

        // then
        assertThat(found).isPresent();
        assertThat(found.get().id()).isEqualTo(saved.id());
        assertThat(found.get().lastPolledAt()).isEqualTo(LAST_POLLED_AT);
    }

    @Test
    void findByChannelAndCursorType_은_부재하면_empty() {
        // when — 저장 없이 조회
        Optional<PollingCursor> found = adapter.findByChannelAndCursorType("NAVER", "ORDER");

        // then
        assertThat(found).isEmpty();
    }
}
