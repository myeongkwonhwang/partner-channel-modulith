package io.github.orange2652.partner.channel.saga.ordr.infra;

import org.junit.jupiter.api.Tag;
import org.springframework.boot.autoconfigure.ImportAutoConfiguration;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.flyway.autoconfigure.FlywayAutoConfiguration;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

/**
 * saga.ordr infra 슬라이스 공통 베이스 — 실제 PostgreSQL(Testcontainers) + Flyway saga baseline.
 *
 * <p>saga.ordr 슬라이스는 {@code saga_schema} 만 건드린다(saga_state / processed_event). 다른 모듈 슬라이스와
 * 동일한 이유로 saga baseline 1개만 적용한다(4 schema 의 {@code V1__...} 를 한 locations 로 합치면 Flyway 가
 * "version 1 중복"으로 거부). {@code @Table(schema=)} 라우팅 검증 목적상 saga_schema baseline 만으로 충분하다.</p>
 *
 * <p>JPA 는 {@code ddl-auto=none} — 컨텍스트에 스캔된 다른 모듈 엔티티·Modulith {@code event_publication} 까지
 * validate 하려다 실패하는 것을 피하고, 실제 INSERT/SELECT 동작으로 라우팅·제약을 직접 단언한다.
 * {@code @ServiceConnection} 이 컨테이너 DataSource 를 자동 연결한다.</p>
 *
 * <p><b>실행 전제: Docker 필요.</b> 미가동 시 컨테이너 기동 단계에서 본 슬라이스(및 하위)가 건너뛰어진다 —
 * {@code @Tag("testcontainers")} 로 필터링한다.</p>
 */
@DataJpaTest
@ImportAutoConfiguration(FlywayAutoConfiguration.class)
@Testcontainers
@Tag("testcontainers")
public abstract class PersistenceSliceTest {

    @Container
    @ServiceConnection
    static final PostgreSQLContainer POSTGRES = new PostgreSQLContainer("postgres:16-alpine");

    @DynamicPropertySource
    static void flywayProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.flyway.enabled", () -> "true");
        registry.add("spring.flyway.create-schemas", () -> "true");
        registry.add("spring.flyway.schemas", () -> "saga_schema");
        registry.add("spring.flyway.default-schema", () -> "saga_schema");
        registry.add("spring.flyway.locations", () -> "classpath:db/migration/saga");
        registry.add("spring.jpa.hibernate.ddl-auto", () -> "none");
    }
}
