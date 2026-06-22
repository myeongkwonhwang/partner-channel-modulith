# A1(주문) SAGA — 코드 동작 흐름

> 외부 채널에 주문이 들어온 뒤 "우리 시스템이 그 주문을 확정하기까지" 의 흐름을, **모듈 / 이벤트 / 트랜잭션 경계** 관점에서 설명합니다.
>
> ⚠️ **현재 구현 상태 (2026-06-22)**: 아래 흐름은 R1~R6 에서 **설계 확정** 됐고, 코드로는 **sagaStart → step1 unconfirmedOrder → step2 validate → step3 channelConfirm + R4 보상(외부 cancel→staging LIFO)까지 와이어링 완료**, 그리고 **토스 채널 구현체(ConfirmStrategy/CompensationPort/OrderQueryPort)까지 구현**됐습니다. 남은 것은 **step4 confirmedOrder(Pivot) 배선**(현재 `ConfirmedOrderCommand` 가 발행되나 core 소비자 미배선=dangling)과 **@SpringBootTest 통합 검증**입니다. 검증은 단위(Port mock) 기준 green이며, 실제 비동기·Tx·outbox는 통합 테스트에서 확인 예정입니다.

---

## 1. 등장인물 (4 모듈 + shared)

| 모듈 | 책임 | DB schema |
|------|------|-----------|
| `batch` | 외부 채널 polling → 주문 수신 트리거 발행 | channel_schema (cursor) |
| `adapter` | 외부 채널 API 호출(수락/취소) + staging 정규화 | channel_schema (staging) |
| `core` | 자사 도메인 — 판매가능 검증 / 주문 확정 / 물류 전송 | core_schema (orders) |
| `saga` | 흐름 오케스트레이션 — step 전진 / 보상 / timeout | saga_schema (saga_state) |
| `shared` (OPEN) | 공통 — `Channel` / 예외 base / 이벤트 계약 / 멱등 추상 | (테이블 없음) |

**모듈 간 직접 호출은 금지** 입니다. 오직 `shared/event/ordr` 의 이벤트 record 를 주고받습니다(Spring Modulith Application Events).

---

## 2. 전체 흐름 (정상 경로)

```
[외부 채널]                  channel_schema      saga_schema        core_schema
    │
    │ (1) batch 가 30분 경과 주문 polling (R1)
    ▼
 batch ──OrderReceivedEvent──▶ @Externalized Kafka "channel.order.received"  (외부 알림)
    │                          └─ in-VM ─▶ saga.sagaStart (Kafka 되읽지 않음, R2)
    │                                         │ saga_state INSERT (RUNNING)
    │                                         ▼
    │                          ┌──── UnconfirmedOrderCommand ────▶ adapter
    │                          │        step1: staging INSERT (외부 전이 없음, R2)
    │                          ◀──── UnconfirmedOrderReply(OK) ───┘
    │                          │
    │                          ├──── ValidateCommand ────────────▶ core
    │                          │        step2: 판매가능 검증 (read-only, DB 안 씀)
    │                          ◀──── ValidateReply(PASSED) ───────┘
    │                          │
    │                          ├──── ChannelConfirmCommand ──────▶ adapter
    │                          │        step3: 외부에 "수락" 통보 (ConfirmStrategy, Tx 밖)
    │                          │              토스=PREPARING_PRODUCT 전이 / 네이버·쿠팡=발주확인 API
    │                          ◀──── ChannelConfirmReply(OK) ─────┘
    │                          │
    │                          ├──── ConfirmedOrderCommand ──────▶ core
    │                          │        step4 (Pivot ★): 물류 전송(Tx 밖) → orders INSERT(Tx)
    │                          ◀──── ConfirmedOrderReply(OK) ─────┘
    │                                         │ saga_state = COMPLETED
    ▼                                         ▼
```

### step 요약

| # | step | 모듈 | 분류 | 하는 일 |
|---|------|------|------|---------|
| 0 | orderReception | batch | 트리거 | 30분 경과 주문만 `OrderReceivedEvent` 발행 |
| — | sagaStart | saga | 부트스트랩 | 이벤트 수신 → saga 인스턴스 생성 → step1 발행 |
| 1 | unconfirmedOrder | adapter | Compensatable | staging 미확정 적재(외부 전이는 안 함) |
| 2 | validate | core | read-only | 판매가능 검증(실패=비즈니스 abort) |
| 3 | channelConfirm | adapter | Compensatable | **검증 통과 후** 외부에 수락 통보 |
| 4 | confirmedOrder | core | **Pivot ★** | 물류 전송 + 내부 주문 확정 INSERT |

**Pivot(step4)** 을 지나면 자동 보상이 불가합니다 — 그 전(step1~3)까지만 되감을 수 있습니다.

---

## 3. 왜 이렇게 나눴나 (핵심 설계 포인트)

### (a) 외부 "수락 통보" 를 검증 뒤로 뺀 이유 — R2 C′
MSA 는 step1 에서 staging 적재 + 외부 PREPARING_PRODUCT 전이를 함께 했습니다. 모듈리스는 **외부 통보를 `channelConfirm`(step3)으로 분리**해 **validate(read-only)를 먼저** 통과시킵니다.
→ 검증 실패 시 외부에 아직 아무 통보도 안 했으므로 **외부 취소(보상) 호출이 필요 없습니다.** 보상 케이스가 줄어듭니다.

### (b) saga 시작은 in-VM — R2 Q2
`OrderReceivedEvent` 는 `@Externalized` 로 Kafka(`channel.order.received`)에도 나가지만, 그건 **외부 알림용** 입니다. saga 는 그 Kafka 를 되읽지 않고 **같은 JVM 안 in-VM 이벤트**로 시작합니다(self-consume 안티패턴 회피). 다중 인스턴스로 분산이 필요해지면 그때 "내부 전용 토픽" 으로 전환합니다.

### (c) 채널 차이는 Strategy 로 흡수 — R5b
같은 `ChannelConfirmCommand` 라도 채널마다 외부 호출이 다릅니다.
- 토스: `PUT status` PAID→PREPARING_PRODUCT (전이가 곧 수락)
- 네이버: 발주확인 전용 API(`placeOrderStatus=OK`)
- 쿠팡: 상품준비중 전용 endpoint(`INSTRUCT`)

→ `ConfirmStrategy` / `CompensationPort` 같은 Port 인터페이스 + 채널별 구현체 + `Map<Channel, Strategy>` 디스패치(Registry)로 처리합니다. 토스 구현체는 `adapter.ordr.infra.toss`(OAuth2 토큰 + RestClient)에 있고, 네이버·쿠팡은 spec 확정 후(R5b D-5) 같은 Port 뒤에 붙습니다.

### (d) 외부 호출은 Tx "밖" — 외부-호출 step 의 리스너 컨벤션 (R6)
step3 channelConfirm·R4 보상 cancel 처럼 **외부 API 를 호출하는 step** 은 step1/step2(순수 DB)와 리스너 구조가 다릅니다. `@ApplicationModuleListener` 는 본문 전체를 한 Tx 로 감싸 외부 호출이 Tx 에 갇히므로, 외부-호출 step 은:
- 리스너를 **`@TransactionalEventListener(AFTER_COMMIT)`(비-Tx)** 로 두고,
- 멱등 마킹·reply 발행만 **`AdapterTxSteps`(각각 `@Transactional(REQUIRES_NEW)`)** 협력 빈으로 분리,
- 외부 호출(`ConfirmStrategy.confirm` / `CompensationPort.cancel`)은 그 두 Tx **사이**에서 Tx 없이 실행합니다(협업원칙: 외부 WRITE 는 Tx 밖).

크래시 복구는 outbox 맹목 재전달이 아니라 R5a timeout scanner + 사전 GET 가드(`OrderQueryPort`, 외부 진실 기반)가 담당합니다.

---

## 4. 멱등성 — 같은 이벤트가 두 번 와도 한 번만 (R3)

at-least-once 환경(이벤트 재전달, outbox republish)이라 **모든 수신 지점에 멱등 가드** 가 필요합니다.

```
listener 진입
   ├─ (1) idempotencyGuard.markIfFirst(consumerName, eventId)           ← 같은 로컬 Tx
   │        false(이미 처리됨) → 즉시 skip
   ├─ (2) 비즈니스 처리 (DB write)                                       ← 같은 Tx
   └─ (3) reply 발행
   ※ 외부 API 호출은 이 Tx "밖" (협업원칙 — 외부-호출 step 은 §3(d) 컨벤션)
```

> 멱등 가드는 **모듈별 전용 Port**(`SagaIdempotencyGuard` / `CoreIdempotencyGuard` / `AdapterIdempotencyGuard` / `BatchIdempotencyGuard`)입니다. 초기 설계의 단일 `shared.ProcessedEventRepository` 는 구현 4개 주입 모호성(`NoUniqueBeanDefinition`) 때문에 모듈별 Port 로 분리했습니다(R3/D-3 조정). 물리 테이블 구조만 `shared` `@MappedSuperclass` 로 공유합니다.

- 키: `eventId = "{channel}:{externalOrderProductId}"` (`EventKey`), 처리 주체: `consumerName`(예: `"channelConfirm"`, `"compensate:unconfirmedOrder"`)
- 가드 구현은 JPA `save()` 가 아니라 **native `INSERT ... ON CONFLICT DO NOTHING` + 영향 행 수** 로 원자성 보장.
- 테이블은 모듈마다 자기 schema 에 1벌씩(`saga_schema` / `core_schema` / `channel_schema`). 공통 구조는 `shared` 의 `@MappedSuperclass` 가, 물리 위치는 각 모듈의 `@Table(schema=)` 가 표현합니다.
- **발행측 vs 수신측 책임 분리**: Spring Modulith 의 내장 outbox(`event_publication`)는 "이벤트가 최소 한 번은 간다"(유실 방지)를, `processed_event` 는 "두 번 와도 한 번만 처리"(중복 흡수)를 담당합니다. 둘은 직교합니다.

---

## 5. 실패하면 — 보상 (R4)

`validate` 거절이나 `channelConfirm`/`unconfirmedOrder` 실패 시, saga 는 **역순(LIFO)** 으로 되감습니다.

| 실패 지점 | 보상 동작 |
|-----------|-----------|
| step2 validate 거절 | **외부 seller-cancel(PAID 결제 종결=환불) + step1 staging CANCELED** (아래 ※) |
| step3 channelConfirm 실패 | (외부 이미 전이됐으면) 외부 cancel → step1 staging 취소 |
| step4 confirmedOrder 부분 실패 | 외부 물류 OK + 내부 INSERT 실패 → `PENDING_RECONCILIATION`(worker 재시도) |

- 외부 취소는 `CompensationPort` 뒤로 격리. **보상 전 사전 GET** 으로 외부 현재 상태를 확인해 이미 취소면 skip(멱등).
- 외부 cancel API 가 미가용이면 자동 호출하지 않고 `PENDING_MANUAL_CANCEL` 로 두어 운영자가 개입합니다(`ChannelConfirmCompensated.MANUAL_REQUIRED`).

### ※ validate 거절은 "이상 신호" — 3중 안전망

직관적으로는 "검증 실패면 그냥 안 처리하면 되지" 같지만, 그러면 **고객이 이미 결제(PAID)한 주문이 토스에 방치** 됩니다. 토스는 미처리 주문을 무패널티로 자동 취소해주지 않습니다 — 발송기한(주문일+3영업일) 경과 시 **1점 + 상품 미노출**, 14일 방치 시 **10점(이용정지)**. 반면 seller-cancel 은 **4점** 단발. 그래서 **적극 종결(seller-cancel)이 방치보다 덜 나쁩니다**(무패널티 출구는 없음).

게다가 채널이 결제까지 받은 주문을 우리가 "판매 불가" 라고 한다는 건 **양사 데이터가 어긋났다**(재고/가격/매핑 sync 장애)는 신호일 수 있습니다 — 한 건이 아니라 다수에 영향일 수 있습니다. 그래서 다음 3중 안전망을 둡니다:

1. **알림** — validate 거절 시 식별정보(channel/externalOrderProductId/sagaId/reason)와 함께 `log.info` + 메트릭 `validate.rejected{channel, reason}`. 운영팀이 불일치를 즉시 인지.
2. **단건 안전 종결** — seller-cancel 로 결제 환불(`deliveryPenaltyCharger=MERCHANT`). 사전 GET 에서 구매자가 먼저 취소했으면 NoOp → 우리 4점도 회피.
3. **대량 제동(게이팅)** — 거절이 **급증** 하면 자동 seller-cancel 을 멈추고 `PENDING_MANUAL` 로 보류 + 알림 → 운영자 확인 후 재개. systemic 버그가 멀쩡한 주문을 대량 오취소(건당 4점 → 이용정지 급속 돌파)하는 것을 막습니다.

> 즉 validate 거절은 **정상 범위면 자동 종결, 급증하면 자동 제동** 입니다. (근거: R4 보강 + 토스 패널티 audit 2026-06-18)

---

## 6. 지금 코드에 존재하는 것 (2026-06-22)

`shared/event/ordr` 의 이벤트 계약은 물론, **sagaStart~step3 + R4 보상의 오케스트레이션 로직과 토스 구현체까지** 코드로 있습니다.

| 흐름 요소 | 상태 | 대응 코드 |
|-----------|------|-----------|
| 트리거 | ✅ | `OrderReceivedEvent`(`@Externalized`) + saga `OrderSagaStarter`(in-VM 수신) |
| step1 unconfirmedOrder | ✅ | adapter `UnconfirmedOrderHandler` + saga `UnconfirmedReplyHandler` |
| step2 validate | ✅ | core `ValidateHandler`/`Validator` + saga `ValidateReplyHandler` |
| step3 channelConfirm | ✅ | adapter `ChannelConfirmHandler`(+`AdapterTxSteps`/`ConfirmStrategyRegistry`) + saga `ChannelConfirmReplyHandler` |
| step4 confirmedOrder | ⛔ dangling | `ConfirmedOrderCommand` 발행되나 **core 소비자 미배선** (다음 작업) |
| 보상(R4) | ✅ | saga `OrderSagaCompensator`/`ChannelConfirmCompensatedHandler`/`UnconfirmedOrderCompensatedHandler` + adapter `ChannelConfirmCompensationHandler`/`UnconfirmedOrderCompensationHandler` + `CompensationPortRegistry` |
| 토스 구현체 | ✅ | `adapter.ordr.infra.toss`: `TossApiClient`/`TossTokenManager`/`TossConfirmStrategy`/`TossCompensationPort`/`TossOrderQueryAdapter` |
| 사전 GET 가드 | ✅(토스) | `OrderQueryPort` + `TossOrderQueryAdapter`(raw 19값→`ExternalOrderStatus` 추상 매핑) |
| 멱등 | ✅ | 모듈별 `*IdempotencyGuard` + `EventKey`(native ON CONFLICT) |
| 취소 사유 | ✅ | `CancelReason`(`DeliveryPenaltyCharger` charger 1급) — R6 |
| 채널 | ✅ | `Channel`(TOSS/NAVER/COUPANG) |

상관 키 `sagaId` 는 MSA 가 Kafka 헤더로 운반하던 것을, in-VM 이벤트인 모듈리스에서는 **record payload 에 명시** 합니다.

### 아직 없는 것
- **step4 confirmedOrder(Pivot)** 배선 — core 소비자(물류 전송 Tx 밖 → orders INSERT Tx) + saga `ConfirmedOrderReplyHandler`(→COMPLETED) + reconciliation
- **@SpringBootTest 통합 검증** — 실제 비동기·Tx·outbox dispatch(지금까지 단위 Port mock)
- `PollingStrategy`(신규 주문 수집) + 네이버·쿠팡 구현체(R5b D-5 격리)
- timeout scanner / reconciliation worker / DLQ + ShedLock (R5a, Phase 4)
- 토스 sandbox 실호출 검증(`TossApiClient` 는 단위에서 mock)
- Phase 3 — B1 송장 SAGA + `@Externalized` Kafka

---

> 결정의 "왜" 는 auto-memory(`project_pcmod_r1`~`r6_toss_port_refinement`, `project_pcmod_toss_adapter_spec_2026_06_22`)에, 그날그날 한 일은 [`docs/worklog/`](../worklog/) 에 있습니다.
