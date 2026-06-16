---
name: saga-event-design
description: Spring Modulith Application Events 기반 SAGA step 설계 워크플로우. event 정의 + listener 위치 + Compensatable/Pivot/Retriable 분류 + 외부+DB 일관성 + 보상 멱등성 + @Externalized 매핑을 단계적으로 진행한다. "step N 설계", "Pivot 위치", "보상 흐름 추가", "Application Event 추가", "@Externalized 매핑", "외부 cancel 자동 호출", "신규 SAGA 흐름" 요청 시 반드시 사용.
---

# SAGA Event Design — partner-channel-modulith

본 프로젝트의 SAGA 흐름 설계를 Modulith Application Events 기반으로 사용자와 합의하며 진행하기 위한 워크플로우.

## 왜 이 워크플로우인가

본 프로젝트는 **MSA 의 Kafka command/reply 토픽을 Modulith Application Events 로 매핑**. 기능은 MSA 의 결정 (Q1 오케 일관 / Q2 자체 구현 / Q4 Idempotency-Key + Outbox dedup / A1 4 step / step 1 외부+DB 일관성 / ADR-0003~0005) 을 승계하지만, 통신 방식이 다르므로 **매핑 결정** 이 필요하다. 본 스킬은 "MSA 결정 참조 → event 매핑 옵션 비교 → 사용자 합의 → 산출물" 순서를 강제한다.

## Step 0 — 기존 결정 조회 (필수, 생략 금지)

새 step 설계 / 보상 흐름 / Pivot 변경 / 모듈 경계 변경 요청을 받으면 가장 먼저 다음을 읽는다.

1. **본 프로젝트 메모리 인덱스**: `/Users/mk/.claude/projects/-Users-mk-repository-mk-partner-channel-modulith/memory/MEMORY.md`
2. **MSA 참조 메모리** (기능 정의 — 동일 보존): `/Users/mk/.claude/projects/-Users-mk-repository-mk-partner-channel-msa/memory/MEMORY.md`
3. 인덱스에서 다음 패턴의 메모리 본문 (요청 영역과 겹치면 필수):
   - 본 프로젝트: `project_pcmod_*`
   - MSA: `project_pcm_q*`, `project_pcm_a1_saga_definition`, `project_pcm_saga_redefinition`, `project_pcm_step{N}_*`, `project_pcm_external_db_consistency_decision`, `project_pcm_saga_pivot_compensation_summary`, `project_pcm_pivot_reconciliation_design`, `project_pcm_saga_timeout_policy`, `project_pcm_dlq_policy`
4. 기존 결정과 모순 가능성 발견 시 → 사용자에게 "기존 [[memory-name]] 과 모순됩니다. ① 기존 유지 ② supersede 후 변경 ③ MSA 와 분기" 선택 요청.

## Step 1 — 요건 분해

다음 6 가지를 명확히 한다. 입력 부족 시 사용자에게 직접 묻는다.

| 항목 | 결정 |
|------|------|
| 어느 흐름 | A1 / B1 / 신규 |
| **책임 module** | batch / adapter / core / saga |
| 외부 호출 | 있음 (어떤 API) / 없음 |
| DB 쓰기 | schema.table / 없음 |
| 멱등성 요건 | 동일 event 재처리 시 동일 결과 보장 방식 |
| Pivot 직전 여부 | 예 / 아니오 (Pivot 이후 step 은 보상 불가) |

## Step 2 — 분류 결정

| 분류 | 정의 | 예 |
|------|------|----|
| **Compensatable** | 실패 시 이전 step 들이 보상 가능. Pivot 이전 step. | step 1 (orderReception), step 2 (validate) |
| **Pivot** ★ | 한 번 성공하면 더 이상 되돌릴 수 없는 지점. SAGA 전체의 commit. | step 3 (confirmedOrder) |
| **Retriable** | Pivot 이후 step. 실패해도 무조건 재시도 (보상 없음). | B1 persistInvoice / dispatchToChannel |

분류가 모호하면 → "이 step 의 외부 효과가 되돌릴 수 있는가?" 를 기준으로 사용자와 합의. 외부 효과가 비가역이면 무조건 Pivot 이거나 Pivot 이후.

## Step 3 — Event 정의 + Listener 위치 결정

본 프로젝트는 Modulith Application Events 기반 — MSA 의 토픽/header 매핑을 다음 패턴으로 대체:

| MSA 컨셉 | Modulith 매핑 |
|----------|---------------|
| `saga.order.cmd` 토픽 + `command-type=UNCONFIRMED_ORDER_REQUEST` header | `UnconfirmedOrderRequested` event record (Java record) |
| `saga.order.reply` 토픽 + `command-type=UNCONFIRMED_ORDER_REPLY_OK` header | `UnconfirmedOrderCompleted` event record |
| consumer 정확 매칭 filter | `@ApplicationModuleListener` + event type 자체로 분리 — prefix 충돌 자동 회피 |
| Outbox 발행 | `spring-modulith-events-jpa` 내장 outbox (자동) |

**event 정의 위치 결정**:
- 여러 module 이 참조하는 event → `shared/` 의 `event/` 패키지
- 한 module 내부 event → 해당 module 의 `internal/event/` 패키지 (다른 module 에 노출 X)

**listener 위치**:
- `@ApplicationModuleListener` (비동기) — 호출자 Tx 와 분리
- 같은 module 안 동기 처리는 `@EventListener` (필요 시만)

## Step 4 — 외부 + DB 일관성 정책 적용

본 프로젝트는 MSA 결정 (`project_pcm_external_db_consistency_decision`) 승계: **step 1 은 "외부 먼저 + DB Tx"**. 외부 호출은 `@Transactional` 밖, 응답 후 별도 Tx.

신규 step 설계 시 적용 방식:

| 패턴 | 사용 시점 | 보상 |
|------|----------|------|
| **외부 → DB** (현 step 1) | 외부 효과 먼저 발생, DB 사후 반영 | DB INSERT 실패 시 → 보상 event publish (spring-modulith-events outbox 자동 보장) |
| **DB → 외부** | 외부 호출 멱등 (PUT) 이거나 보상 불가 | DB 먼저 commit, 외부 실패 시 → 재시도 event publish |
| **DB only** | 외부 호출 없음 | 일반 Tx |
| **외부 only** | DB 쓰기 없음 (status query 등) | 보상 불필요 |

신규 패턴이 필요하면 → 본 스킬을 갱신하기 전에 사용자와 합의.

## Step 5 — 멱등성 결정

본 프로젝트는 MSA Q4 (`project_pcm_q4_compensation_idempotency`) 승계: **IdempotencyGuard + spring-modulith-events outbox dedup**.

- 외부 호출 멱등성: `Idempotency-Key` 헤더 (외부 API 지원 시) — 토스는 미지원이므로 사전 조회 + 3-state + recovery 패턴 (MSA `project_pcm_b1_send_invoice_design` 승계)
- Event 멱등성: spring-modulith-events outbox (자동) — at-least-once 보장
- Consumer 멱등성: `IdempotencyGuard` + `processed_event` 테이블 — `eventId` 기반 dedup
- 보상 멱등성: 보상 listener 도 동일 가드

## Step 6 — @Externalized 매핑 (외부 Kafka publish)

외부 트리거 / 외부 시스템 알림은 `@Externalized` 로 Kafka publish.

| Event | @Externalized 대상 | Kafka 토픽 |
|-------|-------------------|------------|
| `OrderReceived` (외부 채널 polling 결과) | 예 | `channel.order.received` |
| `InvoiceReceived` (자사 LogisticsGateway polling 결과) | 예 | `logistics.invoice.received` |
| 모든 내부 saga event (Request/Completed/Failed 등) | 아니오 | — |

**판단 기준**: 외부 시스템 / MSA 서비스 / 다른 운영 환경과의 통신이면 `@Externalized`. 모듈 간 내부 통신이면 일반 Application Event.

## Step 7 — 산출물 작성

아래 형식으로 출력. 그대로 사용자에게 보고 + `doc-keeper` 에게 메모리 기록 위임.

```
### {흐름명} step {N}: {step 이름}

**분류**: Compensatable | Pivot ★ | Retriable
**책임 module**: batch | adapter | core | saga
**트리거 event** (수신): `{EventRecordName}` (위치: shared 또는 {module}/internal/event)
**발행 event**: `{EventRecordName}` (위치: 동일)
**Listener**: `@ApplicationModuleListener` (비동기, Tx 분리)
**외부 호출**: {API or 없음} | 멱등성 처리: {Idempotency-Key | 사전 조회 + 3-state}
**DB 쓰기**: {schema.table — INSERT/UPDATE | 없음}
**외부+DB 패턴**: 외부 → DB | DB → 외부 | DB only | 외부 only
**@Externalized**: 예 (토픽 {name}) / 아니오
**보상**:
  - 보상 event: 존재 — `{CompensateEventRecord}` | 불필요 (Pivot 이후 / Pivot)
  - 멱등성: IdempotencyGuard 적용
**Pivot 직전 여부**: 예 / 아니오
**결정 근거** (1~3 줄)
**기존 메모리 정합성**: [[link]] 와 충돌 없음 / 충돌 — {해결}
**MSA 승계**: [[msa-memory]] 와 기능 동일 — Modulith 매핑 차이만
```

## Step 8 — 메모리 기록 위임

산출물이 사용자 합의 받으면 `doc-keeper` 에게 메시지:
```
[modulith-architect → doc-keeper] 다음 결정 메모리 기록 요청
slug: project_pcmod_step{N}_{name}
type: project
사유: ...
적용: ...
관련 링크: 본 프로젝트 [[...]], MSA 참조 [[project_pcm_a1_saga_definition]]
```

## 협업

- 외부 API 규격 불명확 → `channel-spec-analyst` 에게 위임
- 의존성 방향 / 모듈 경계 / `ApplicationModules.verify()` 충돌 → `hexa-clean-reviewer` 에게 사전 검토 요청

## 안티패턴 (감지 시 즉시 사용자에게 알림)

- 외부 호출이 `@Transactional` 안에 있다고 가정된 설계
- module 간 직접 호출 (Application Event 가 아님)
- internal event 가 `@Externalized` 로 외부 누출
- `@ApplicationModuleListener` 가 동기 처리 (Tx 흡수 위험)
- 보상 listener 에 멱등성 보호 없음
- 사용자 합의 없이 기존 메모리 결정을 뒤집는 설계
