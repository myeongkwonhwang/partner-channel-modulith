# partner-channel-modulith

`partner-channel-msa` (MSA 4 service + 6 lib) 와 **기능적으로 동일한** 멀티채널 (토스 / 쿠팡 / 네이버 / 유튜브쇼핑) 주문/송장 흐름을 **Spring Modulith (단일 Spring Boot + 모듈 경계)** 로 재구성한 학습 프로젝트.

## 결정 사항

| 항목 | 결정 |
|------|------|
| 구조 | 단일 Spring Boot + 4 module 1:1 매핑 (channelBatch / channelAdapter / serviceCore / sagaOrchestrator) + shared (open module) |
| 내부 통신 | **Spring Modulith Application Events** (`@ApplicationModuleListener`) + 외부 Kafka 발행은 `@Externalized` |
| DB | 같은 DB + schema 분리 (`channel_schema` / `core_schema` / `saga_schema` / `logistics_schema`) |
| 외부 Kafka | 채널 (토스) 호출 + 외부 트리거 (channel.order.received / logistics.invoice.received) 만 — 모듈 간 통신은 Application Events |
| SAGA | A1 (orderReception → unconfirmedOrder → validate → confirmedOrder Pivot) + B1 (persistInvoice + dispatchToChannel) — `partner-channel-msa` 동일 |
| 의존 검증 | Spring Modulith `ApplicationModules.verify()` + ArchUnit |
| 빌드 | Java 21 + Spring Boot 3.4.5 + Spring Modulith 1.3.3 + Gradle Groovy DSL |

## 동일 보존 — `partner-channel-msa` ADR 적용

- ADR-0001 ~ ADR-0005 (Modulith 환경에 맞게 코드 매핑)
- 핵심: 외부 + DB Tx 금지 / log.info / 도메인 예외 계층 / Pivot reconciliation / SAGA TIMEOUT / DLQ
- 본 프로젝트 `docs/adr/` 에 modulith 매핑 ADR 별도 작성 예정

## 실행

```bash
docker-compose up -d        # PostgreSQL 5433 + Kafka 9093 + Kafka UI 8091
./gradlew bootRun           # http://localhost:8080
./gradlew test              # Modulith verification 포함
```

## MSA 버전과의 포트 분리

| 항목 | MSA | Modulith |
|------|-----|----------|
| PostgreSQL | 5432 | 5433 |
| Kafka | 9092 | 9093 |
| Kafka UI | 8090 | 8091 |
| Web | 8081~8084 | 8080 |

두 프로젝트 동시 실행 가능 — 비교 학습용.
