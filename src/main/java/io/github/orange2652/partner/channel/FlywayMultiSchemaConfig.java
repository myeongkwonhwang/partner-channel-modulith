package io.github.orange2652.partner.channel;

import java.util.List;
import javax.sql.DataSource;
import org.flywaydb.core.Flyway;
import org.springframework.boot.flyway.autoconfigure.FlywayMigrationStrategy;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * schema 별 독립 Flyway history — 모듈 경계(D-3: schema=모듈 네임스페이스)를 마이그레이션 이력에도 관철한다.
 *
 * <p>본 프로젝트는 단일 DataSource 에 4 schema(channel/core/saga/logistics)를 둔다. 각 schema baseline 은 모두
 * {@code V1}(모듈별 독립 버전)이라, Spring Boot 의 기본 단일 Flyway 처럼 4 location 을 한 history 로 묶으면
 * {@code Found more than one migration with version 1} 로 거부된다. 그래서 schema 마다 <b>별도 Flyway 인스턴스</b>
 * 를 돌려 각자 {@code flyway_schema_history} 를 그 schema 안에 둔다(독립 버전 관리). 모듈을 MSA 로 추출할 때 각
 * schema 의 이력이 그대로 분리되어 따라간다.</p>
 *
 * <p>{@link FlywayMigrationStrategy} 로 등록해 Boot 자동설정의 실행 순서(Flyway → JPA EntityManagerFactory 검증
 * 전)를 그대로 활용한다 — 자동설정이 만든 단일 {@code Flyway} 빈은 무시하고, 본 전략이 schema 별로 migrate 한다.
 * baseline SQL 은 이미 schema 한정({@code channel_schema.staging_order} 등)이라 테이블은 올바른 schema 에 생성된다.</p>
 */
@Configuration
class FlywayMultiSchemaConfig {

    /**
     * (schema, 마이그레이션 location) 쌍 — 새 schema 추가 시 한 줄만 늘린다. 4 비즈니스 모듈 schema + 공유
     * {@code events_schema}(Spring Modulith 내장 outbox {@code event_publication}, hibernate.default_schema 와 정합).
     */
    private static final List<SchemaModule> MODULES = List.of(
            new SchemaModule("channel_schema", "classpath:db/migration/channel"),
            new SchemaModule("core_schema", "classpath:db/migration/core"),
            new SchemaModule("saga_schema", "classpath:db/migration/saga"),
            new SchemaModule("logistics_schema", "classpath:db/migration/logistics"),
            new SchemaModule("events_schema", "classpath:db/migration/events"));

    @Bean
    FlywayMigrationStrategy multiSchemaMigrationStrategy(DataSource dataSource) {
        return autoConfigured -> {
            for (SchemaModule module : MODULES) {
                Flyway.configure()
                        .dataSource(dataSource)
                        .schemas(module.schema())
                        .defaultSchema(module.schema())
                        .createSchemas(true)
                        .locations(module.location())
                        .load()
                        .migrate();
            }
        };
    }

    private record SchemaModule(String schema, String location) {
    }
}
