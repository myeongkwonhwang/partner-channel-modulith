package io.github.orange2652.partner.channel.batch.ordr.infra;

import org.junit.jupiter.api.Tag;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.flyway.autoconfigure.FlywayAutoConfiguration;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.boot.autoconfigure.ImportAutoConfiguration;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

/**
 * batch.ordr infra 슬라이스 공통 베이스 — 실제 PostgreSQL(Testcontainers) + Flyway 4 schema baseline.
 *
 * <p>본 프로젝트 불변식(@Table schema 라우팅, 복합 PK, native {@code ON CONFLICT} 원자성)은 H2 와
 * 의미가 달라 실제 PostgreSQL 로만 검증 가능하다(test-write SKILL Step 4-1, H2 금지).</p>
 *
 * <p><b>실행 전제: Docker 필요.</b> Docker 미가동 환경에서는 컨테이너 기동 실패로 본 슬라이스(및 하위)
 * 테스트가 건너뛰어진다 — {@code @Tag("testcontainers")} 로 분리해 필요 시 필터링한다.</p>
 *
 * <p>컨테이너는 {@code static}(클래스 1개 재사용). Flyway 는 application.yml 과 동일하게 4 schema 를
 * 생성·마이그레이션하도록 동적 프로퍼티로 설정하고, JPA 는 {@code ddl-auto=validate} 로 매핑 정합만 본다.
 * {@code @ServiceConnection} 이 컨테이너 DataSource 를 자동 연결하므로(Boot 4 기본 {@code Replace.NON_TEST}
 * 는 이 실제 연결을 치환하지 않는다) 별도 {@code @AutoConfigureTestDatabase} 는 두지 않는다.</p>
 */
@DataJpaTest
@ImportAutoConfiguration(FlywayAutoConfiguration.class)
@Testcontainers
@Tag("testcontainers")
public abstract class PersistenceSliceTest {

    @Container
    @ServiceConnection
    static final PostgreSQLContainer POSTGRES = new PostgreSQLContainer("postgres:16-alpine");

    /**
     * batch.ordr 슬라이스는 {@code channel_schema} 만 건드린다(polling_cursor / processed_event). 따라서
     * Flyway 는 channel baseline 1개만 적용한다 — 4 schema 의 {@code V1__...} 를 한 locations 로 합치면
     * Flyway 가 "version 1 중복"으로 거부하기 때문이다(이 충돌은 운영 application.yml 의 4-location 단일
     * history 구성과 동일 위험으로, 슬라이스에서는 채널 schema 만 격리해 회피한다). {@code @Table(schema=)}
     * 라우팅 검증 목적상 channel_schema baseline 만으로 충분하다.
     *
     * <p>JPA 는 {@code ddl-auto=none}. {@code validate} 를 쓰면 컨텍스트에 스캔된 다른 모듈 엔티티
     * ({@code core_schema.orders} 등)·Modulith {@code event_publication} 까지 검증하려다 실패한다 —
     * 본 슬라이스는 channel_schema 만 마이그레이션하므로 검증을 끄고, 실제 INSERT/SELECT 동작으로
     * 라우팅·제약을 직접 단언한다(테이블이 잘못 매핑되면 쿼리 단계에서 실패).</p>
     */
    @DynamicPropertySource
    static void flywayProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.flyway.enabled", () -> "true");
        registry.add("spring.flyway.create-schemas", () -> "true");
        registry.add("spring.flyway.schemas", () -> "channel_schema");
        registry.add("spring.flyway.default-schema", () -> "channel_schema");
        registry.add("spring.flyway.locations", () -> "classpath:db/migration/channel");
        registry.add("spring.jpa.hibernate.ddl-auto", () -> "none");
    }
}
