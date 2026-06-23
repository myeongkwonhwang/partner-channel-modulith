-- Spring Modulith 내장 outbox (JPA 이벤트 발행 레지스트리) — 공유 인프라이므로 모듈 schema 와 분리된 events_schema 에 둔다.
-- DDL 은 spring-modulith-events-jdbc 2.0.7 의 정본 schema-postgresql.sql(v2, UPDATE 완료 모드) 을 그대로 사용한다
--   (org/springframework/modulith/events/jdbc/schemas/v2/schema-postgresql.sql).
-- spring-modulith-events-jpa 의 DefaultJpaEventPublication 엔티티는 @Table(schema) 미지정이라 hibernate.default_schema
--   (=events_schema) 로 라우팅되며, 본 테이블과 컬럼 타입이 1:1 매칭되어 ddl-auto=validate 를 통과한다.
-- 배치 위치(events_schema)는 FlywayMultiSchemaConfig 의 전략 엔트리가 결정한다(unqualified → defaultSchema).
CREATE TABLE IF NOT EXISTS event_publication
(
  id                     UUID NOT NULL,
  listener_id            TEXT NOT NULL,
  event_type             TEXT NOT NULL,
  serialized_event       TEXT NOT NULL,
  publication_date       TIMESTAMP WITH TIME ZONE NOT NULL,
  completion_date        TIMESTAMP WITH TIME ZONE,
  status                 TEXT,
  completion_attempts    INT,
  last_resubmission_date TIMESTAMP WITH TIME ZONE,
  PRIMARY KEY (id)
);
CREATE INDEX IF NOT EXISTS event_publication_serialized_event_hash_idx ON event_publication USING hash(serialized_event);
CREATE INDEX IF NOT EXISTS event_publication_by_completion_date_idx ON event_publication (completion_date);
