# CLAUDE.md - partner-channel-modulith

## 하네스: partner-channel-modulith

**목표:** 본 프로젝트의 작업을 4명의 전문 에이전트 (modulith-architect / channel-spec-analyst / hexa-clean-reviewer / doc-keeper) + 글로벌 java-spring-expert 에게 분담하여 Doc-first 원칙을 일관되게 적용.

**트리거:** 본 프로젝트 작업 요청 시 `pcmod-orchestrator` 스킬을 사용. 단순 질문 (단발성 사실 확인 등) 은 직접 응답 가능.

**변경 이력:**
| 날짜 | 변경 내용 | 대상 | 사유 |
|------|----------|------|------|
| 2026-06-16 | 초기 구성 | 전체 (4 에이전트 + 5 스킬) | `partner-channel-msa` 의 하네스를 modulith 컨텍스트로 재구성 |

## 프로젝트 정체성

`partner-channel-msa` (MSA 4 service + 6 lib) 와 **기능적으로 동일** 한 멀티채널 (토스 / 쿠팡 / 네이버 / 유튜브쇼핑) 주문/송장 흐름을 **Spring Modulith (단일 Spring Boot + 모듈 경계)** 로 재구성한 학습 프로젝트.

## 진행 상태

- **현재 단계**: Phase 0 골격 완료 (build PASS) — 4 module 경계 + shared open module + 4 schema baseline + Modulith verify 스캐폴드
- **다음 단계**: Phase 1 (libs 매핑 — 도메인 + Port) → Phase 2 (A1 Application Events) → Phase 3 (B1 + @Externalized Kafka) → Phase 4 (3 ADR 코드) → Phase 5 (Modulith verification + ArchUnit)

## 합의된 결정

| 항목 | 결정 |
|------|------|
| 이름 | `partner-channel-modulith` |
| 목적 | 멀티채널 주문/송장 통합 학습. MSA 와 동일 기능, 구조만 Modulith |
| 범위 | 외부 수신 · 판매 가능 검증 · 내부 주문 생성 · 물류 전송 · 송장 연동 |
| 아키텍처 | **단일 Spring Boot + Spring Modulith** 4 module (channelBatch / channelAdapter / serviceCore / sagaOrchestrator) + `shared` (open module) |
| 채널 어댑터 | multi-tenant Strategy (`adapter` 모듈 안 채널 분기) |
| SAGA 스타일 | 오케 일관 — **Modulith Application Events** 로 step command/reply (기존 MSA 의 Kafka 토픽 대체) |
| 외부 Kafka | 외부 채널/시스템과의 트리거 publish 만 — `@Externalized` 로 발행 (`channel.order.received` / `logistics.invoice.received`) |
| DB 분리 | 같은 DB + schema 분리 (`channel_schema` / `core_schema` / `saga_schema` / `logistics_schema`) |
| 의존 검증 | Spring Modulith `ApplicationModules.verify()` + ArchUnit |
| 언어/빌드 | Java 21 + Spring Boot 3.4.5 + Spring Modulith 1.3.3 + Gradle Groovy DSL + Lombok + ArchUnit |

## 진행 방식 — Doc-first

- 사용자 합의 원칙: **결정 → 짧은 메모리/ADR → 코드**
- 한 결정당 1 단위
- 새 기술/패턴 도입 전 사용자 의견 먼저
- 사용자 페이스 존중 — "다음 작업" 자발 언급 자제
- 본 프로젝트는 `partner-channel-msa` 의 결정 (ADR-0001 ~ 0005, MEMORY 다수) 을 **기능적으로 승계** — Modulith 환경에 맞게 매핑

## MSA ↔ Modulith 핵심 매핑

| MSA 컴포넌트 | Modulith 매핑 |
|--------------|---------------|
| `services/channel-batch` | `batch/` module |
| `services/channel-adapter` | `adapter/` module |
| `services/service-core` | `core/` module |
| `services/saga-orchestrator` | `saga/` module |
| `libs/common-domain` + `libs/kafka-infra` 일부 + `libs/channel-event-schema` 의 record DTO | `shared/` (open module) |
| `libs/persistence-common` | 각 module 안 도메인/infra 패키지로 분산 (schema 별 책임 분리) |
| `libs/logistics-gateway` | `core/` 안 (자사 시스템 — serviceCore 책임) |
| `libs/channel-client` (Toss) | `adapter/` 안 |
| Kafka `saga.order.cmd` / `saga.order.reply` | `@ApplicationModuleListener` + 도메인 event record (`shared/` 또는 `saga/` event 정의) |
| Kafka `saga.invoice.cmd` / `saga.invoice.reply` | 동일 — Application Events |
| Outbox 패턴 | `spring-modulith-events-jpa` — 내장 outbox |
| 외부 Kafka 발행 (`channel.order.received` / `logistics.invoice.received`) | `@Externalized` (`spring-modulith-events-kafka`) |
| MSA Multi-gradle 의존 규칙 | Modulith `@ApplicationModule(allowedDependencies = { "shared" })` + `ApplicationModules.verify()` + ArchUnit |

## 외부 API 규격 확인

- 토스쇼핑 공식 문서는 `mcp__toss-shopping-docs__searchDocumentation` / `getPage` MCP 사용
- 다른 채널 (쿠팡 / 네이버 / 유튜브쇼핑) 도 공식 문서 우선
- 코드/구조 설계가 외부 API 와 맞닿는 부분은 추정 금지 — 공식 문서로 규격 확인 후 작성
- MSA 라운드에서 확인된 토스 6 항목 (`project_pcm_toss_api_spec_audit_2026_06_16`) 은 본 프로젝트에도 동일 적용

## 협업 원칙

- **외부 API + DB 쓰기에 `@Transactional` 금지** — 외부가 WRITE 면 Tx 밖에서 호출. 외부 응답 후 별도 Tx 에서 DB 반영
- **`log.info` 만 사용** — debug/warn/error/trace 자제. 식별 정보는 메시지 본문에 명시
- **로깅 식별 목적의 보조 추상화 자제** — SkipReason enum / Result 타입 등 도입 신중
- **작업 단위 완료 시 git stage** — commit 은 명시 지시 시에만
- **작업 보고 시 "다음 작업" 자발 언급 자제** — 사용자 페이스
- **본 프로젝트 규칙 > 외부 패턴** — Effective Java + Clean Architecture 규칙 우선

## 코딩 규칙

- **헥사고날 아키텍처** (Ports & Adapters)
- **Effective Java 3판**
  - 정적 팩토리 (Item 1): `of()` / `from()`
  - 빌더 (Item 2): 파라미터 많은 Entity
  - 불변성 (Item 17): final 필드, `List.of()`
  - 인터페이스로 참조 (Item 64): Port 인터페이스 타입으로
  - 매직 스트링 금지: Constants 또는 클래스 상수. 채널 식별자는 `Channel` enum (shared)
  - 방어적 접근 (Item 49): `get(0)` 금지, null/empty 방어 메서드
  - 예외 (Item 69/70): **도메인별 unchecked 계층** — `PartnerChannelException` (shared) 베이스 + 각 모듈/도메인에 `abstract` sub (예: `SagaException`, `ChannelException`, `LogisticsException`) + `final` 구체. 메시지 본문에 식별 정보 (sagaId / orderId 등) 명시
  - Optional 남용 금지 (Item 55): 반환값에만. **`Optional.get()` 무방어 호출 금지** — `orElseThrow()` / `orElse(null)` 등 명시적 처리
- **Clean Architecture**
  - 의존성 방향: Presentation → Application → Domain ← Infrastructure
  - Domain: 순수 Java, 프레임워크 의존 금지 (Lombok 만 허용)
  - Port/Adapter: 인터페이스는 Domain, 구현은 Infrastructure
  - Entity: 비즈니스 규칙 보유. JPA Entity 와 Domain Entity 혼용 금지
- **Spring**
  - Controller: 입력 검증 + 위임만
  - Service: `@Transactional` 범위 최소화
  - DTO/Entity 분리
  - 예외 처리: `@ControllerAdvice` 중앙 집중
- **Spring Modulith 특화**
  - module 간 직접 호출 금지 — Application Events 만
  - 외부 publish 는 `@Externalized` — 내부 event 와 분리
  - module API 노출은 **named interface** (`package-info.java` 의 `@NamedInterface`) 로 제한 — 모든 public class 가 노출되지 않도록

## Modulith 의존 규칙 (ApplicationModules.verify() + ArchUnit 강제)

- 각 module (`batch` / `adapter` / `core` / `saga`) → `shared` 만 의존
- module 간 직접 호출 금지 — Application Events 로 통신
- `shared/` 는 open module — 모든 module 이 참조 가능
- `@ApplicationModuleListener` 는 비동기 — 호출자 Tx 와 분리

## Phase 1+ (선택)

- module 내부 도메인별 더 작은 sub-module 분리 (core.ordr / core.claim 분리 시점)
- + notification 모듈 (알림)
- + analytics 모듈 (통계)
- MSA 환원 옵션 — module 별 service 추출 (Spring Modulith → MSA 전환 학습)
