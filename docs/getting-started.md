# partner-channel-modulith — 초보자 가이드

> 처음 보는 사람도 "아, 이 프로젝트가 뭐 하는 거구나" 하고 이해할 수 있게 쓴 문서입니다.

---

## 1. 한 줄 요약

**여러 오픈마켓 (토스쇼핑 · 쿠팡 · 네이버 · 유튜브쇼핑) 에서 들어온 주문을 우리 시스템에서 처리하고, 송장 (택배 번호) 을 다시 채널로 보내주는 시스템** 을 만드는 학습 프로젝트입니다.

겉으로 보면 별거 아닌 것 같지만, "외부 시스템과 통신하면서 데이터가 깨지지 않게" 만드는 게 의외로 어렵습니다. 그래서 이 프로젝트가 존재합니다.

---

## 2. 비유로 먼저 이해해보기

이 시스템을 **편의점 본사의 주문 처리 센터** 라고 생각해 봅시다.

- **편의점 지점 (= 오픈마켓 채널)**: 토스쇼핑 / 쿠팡 / 네이버 / 유튜브쇼핑. 각자 자기 방식으로 주문이 들어옵니다.
- **본사 직원 (= 우리 시스템)**:
  1. 지점에서 주문 들어왔는지 정기적으로 확인하고 (= **batch**)
  2. 지점 양식대로 주문 데이터를 우리 양식으로 번역하고 (= **adapter**)
  3. 본사 장부에 주문/송장을 기록하고 (= **core**)
  4. "이 모든 흐름이 중간에 끊기지 않게" 감독합니다 (= **saga**)

이 4 명의 직원이 **한 사무실에서 일하지만, 각자 책상 (모듈) 이 분리** 되어 있다는 게 핵심입니다.

---

## 3. 왜 이 프로젝트가 있는가?

같은 기능을 하는 형제 프로젝트가 하나 더 있습니다.

| 프로젝트 | 구조 |
|----------|------|
| `partner-channel-msa` | **MSA** — 4 개의 독립된 Spring Boot 서비스 (각자 다른 프로세스 / DB) |
| `partner-channel-modulith` (← **여기**) | **Spring Modulith** — 1 개의 Spring Boot 안에서 4 개의 모듈로 분리 |

기능은 똑같습니다. 구조만 다릅니다. **"굳이 MSA 로 안 쪼개도 모듈 경계만 잘 그으면 비슷한 효과를 얻을 수 있을까?"** — 이걸 비교 학습하는 게 목적입니다.

> 💡 둘은 동시에 실행 가능합니다 (MSA 는 5432/9092/8081~8084, Modulith 는 5433/9093/8080 사용).

---

## 4. 전체 그림

```
   ┌────────────────────────────────────────────────────────────────────┐
   │             외부 채널 (토스쇼핑 / 쿠팡 / 네이버 / 유튜브쇼핑)       │
   └─────────┬─────────────────────────────────────────────┬────────────┘
             │ 주문 polling                  송장 호출 ↑   │
             ↓                                             │
   ╔═════════════════════════════════════════════════════════════════════╗
   ║              partner-channel-modulith (1 Spring Boot)               ║
   ║                                                                     ║
   ║   ┌─────────┐    ┌──────────┐    ┌────────┐    ┌──────────────┐   ║
   ║   │  batch  │ →  │ adapter  │ →  │  core  │ →  │     saga     │   ║
   ║   │         │    │          │    │        │    │ (오케스트레이터) │   ║
   ║   └─────────┘    └──────────┘    └────────┘    └──────────────┘   ║
   ║        ↓              ↓              ↓                 ↓           ║
   ║   ┌────────────────────────────────────────────────────────────┐   ║
   ║   │                       shared (공통)                         │   ║
   ║   └────────────────────────────────────────────────────────────┘   ║
   ║                                                                     ║
   ║   ↓ DB schema 분리                                                  ║
   ║   ┌────────────┬────────────┬────────────┬─────────────────────┐  ║
   ║   │ channel_*  │   core_*   │   saga_*   │   logistics_*       │  ║
   ║   └────────────┴────────────┴────────────┴─────────────────────┘  ║
   ╚═════════════════════════════════════════════════════════════════════╝
                                    ↓
                       PostgreSQL (1 DB, 4 schema)
                       Kafka (외부 트리거 발행만)
```

---

## 5. 4 개 모듈, 각자의 역할

각 모듈은 `src/main/java/io/github/orange2652/partner/channel/<모듈명>/` 에 위치합니다.

### 5.1 `batch` — 주문 가져오는 직원
- 외부 채널에 **"새 주문 있어?"** 하고 주기적으로 (polling) 물어봅니다.
- 새 주문이 있으면 **"주문이 들어왔다!"** 라는 이벤트를 사내에 알립니다.
- MSA 의 `channel-batch` 서비스와 1:1 매핑됩니다.

### 5.2 `adapter` — 외부 채널 번역가
- 외부 채널의 **각자 다른 양식** 을 우리 시스템 양식으로 번역합니다.
- 우리 양식의 명령을 받으면, 다시 외부 채널 양식으로 번역해서 API 를 호출합니다.
- 예: 토스의 `PAID` 상태를 `PREPARING_PRODUCT` 로 전이시키기.
- 무언가 잘못되면 **"이 주문 취소해줘"** 같은 보상 호출도 합니다.

### 5.3 `core` — 본사 장부
- **우리 시스템의 진짜 도메인** (주문 / 송장) 을 관리합니다.
- 외부 채널과는 무관하게, **"우리 회사 입장에서 이 주문은 어떤 상태인가?"** 가 여기 있습니다.
- 자사 물류 시스템 (LogisticsGateway) 도 여기서 호출합니다.

### 5.4 `saga` — 흐름 감독
- 가장 어려운 부분입니다. 다음 절에서 따로 설명합니다.

### 5.5 `shared` — 공용 도구함
- 다른 모듈이 **모두 같이 쓰는 코드** (예: `Channel` enum, 예외 클래스 등).
- 다른 모듈들과 다르게 **open module** 이라 누구나 의존할 수 있습니다.

---

## 6. SAGA 패턴이 뭐고 왜 필요한가?

### 6.1 문제 상황

주문 처리 흐름을 생각해봅시다.

1. 외부 채널에서 주문 받기 → 성공 ✓
2. 채널 측에 "처리 시작" 알리기 (외부 API 호출) → 성공 ✓
3. 우리 DB 에 주문 INSERT → **실패!** ✗

만약 3 번이 실패하면 어떻게 될까요?

- 외부 채널은 우리가 처리 중인 줄 알고 있음
- 우리 DB 에는 주문이 없음
- **→ 데이터 불일치 발생**

이걸 그냥 트랜잭션 (`@Transactional`) 으로 묶으면 될 것 같지만, **외부 API 호출은 트랜잭션에 못 묶습니다**. (네트워크는 롤백이 안 되니까요.)

### 6.2 SAGA 의 해결책

각 단계를 **"이 단계가 실패하면 이전 단계는 어떻게 되돌릴까?"** 까지 정해둔 흐름입니다.

```
정상 흐름:        step1 → step2 → step3 → step4 → 완료
                    │       │       │       │
실패 시 보상:    ← 보상1 ← 보상2 ← 보상3 ← 실패!
```

본 프로젝트의 SAGA 는 2 개입니다.

#### A1 SAGA — 주문 받기 (4 step)
```
orderReception → unconfirmedOrder → validate → confirmedOrder ★
   (batch)        (adapter)         (core)        (core, Pivot)
```

- **Pivot ★** 표시한 step 이 핵심입니다. 이 step 을 **지나면 더 이상 되돌릴 수 없습니다**.
- 보상은 reconciliation worker 가 별도로 재시도합니다.

#### B1 SAGA — 송장 보내기 (2 step)
```
persistInvoice → dispatchToChannel
   (core)          (adapter)
```

### 6.3 모듈끼리 어떻게 대화하나?

**Spring Modulith Application Events** 를 사용합니다.

```java
// saga 모듈이 명령을 던지면
applicationEventPublisher.publishEvent(new ValidateOrderCommand(...));

// core 모듈이 받아서 처리하고
@ApplicationModuleListener
void on(ValidateOrderCommand command) {
    ...
    publishEvent(new OrderValidatedReply(...));
}

// saga 가 다시 응답을 받아서 다음 step 으로 진행
@ApplicationModuleListener
void on(OrderValidatedReply reply) {
    advance(...);
}
```

**MSA 버전이라면 Kafka 토픽** 으로 했을 일을, Modulith 에서는 **Application Event** 로 처리합니다. 그래서 모듈 간 직접 호출이 금지됩니다 (`ApplicationModules.verify()` 로 검증).

---

## 7. 외부 Kafka 는 언제 쓰나?

내부 통신 (모듈 간) 은 Application Events 로 하는데, Kafka 는 **외부 시스템과의 접점** 에만 씁니다.

- `channel.order.received` — 외부 시스템에 "주문 받았다" 알리기
- `logistics.invoice.received` — 외부 시스템에 "송장 받았다" 알리기

코드에서는 `@Externalized` 어노테이션으로 표시합니다. **내부 이벤트와 명확히 분리** 하는 게 포인트입니다.

---

## 8. DB 는 어떻게 구성됐나?

**한 PostgreSQL DB 안에 4 개의 schema** 로 책임을 나눕니다.

| Schema | 책임 | 주요 테이블 |
|--------|------|------------|
| `channel_schema` | 외부 채널 raw 데이터 / staging | `staging_order`, `polling_cursor`, `processed_event` |
| `core_schema` | 자사 도메인 | `orders`, `invoices`, `processed_event` |
| `saga_schema` | SAGA 인스턴스 상태 | `saga_state` |
| `logistics_schema` | 자사 물류 | `shipment` |

> 💡 **왜 schema 만 분리하고 DB 는 안 나눴나?** Modulith 의 학습 목적상 "모듈 경계만 명확하면 충분" 이라는 실험입니다. MSA 였다면 4 개의 독립 DB 였을 겁니다.

Flyway 가 시작 시 자동으로 4 schema 를 만들고 baseline 을 적용합니다.

---

## 9. 폴더 구조 한눈에

```
partner-channel-modulith/
├── build.gradle                          # 1 개의 Spring Boot 빌드 정의
├── docker-compose.yml                    # PostgreSQL + Kafka + Kafka UI
├── docs/
│   ├── adr/                              # 아키텍처 결정 기록 (앞으로 채워질 예정)
│   └── getting-started.md                # ← 지금 이 문서
└── src/main/
    ├── java/io/github/orange2652/partner/channel/
    │   ├── PartnerChannelModulithApplication.java   # main()
    │   ├── batch/   package-info.java               # @ApplicationModule
    │   ├── adapter/ package-info.java               # @ApplicationModule
    │   ├── core/    package-info.java               # @ApplicationModule
    │   ├── saga/    package-info.java               # @ApplicationModule
    │   └── shared/  package-info.java               # @ApplicationModule(Type.OPEN)
    └── resources/
        ├── application.yml                          # 8080 / 5433 / 9093 연결
        └── db/migration/{channel,core,saga,logistics}/
            └── V1__*_baseline.sql                   # Flyway baseline
```

각 모듈 폴더의 `package-info.java` 가 **모듈 정의** 입니다. 예시:

```java
@ApplicationModule(
    displayName = "Channel Batch",
    allowedDependencies = { "shared" }     // ← shared 만 의존 가능
)
package io.github.orange2652.partner.channel.batch;
```

> 이 한 줄이 **"이 모듈은 shared 외에는 다른 모듈을 직접 import 하면 안 된다"** 는 강제 규칙입니다. 어기면 테스트가 실패합니다.

---

## 10. 직접 돌려보기

```bash
# 1. 인프라 띄우기
docker-compose up -d
#   → PostgreSQL    : localhost:5433
#   → Kafka         : localhost:9093
#   → Kafka UI      : http://localhost:8091

# 2. 빌드 + 모듈 경계 검증
./gradlew build
#   → ApplicationModules.verify() 가 통과해야 BUILD SUCCESS

# 3. 애플리케이션 실행
./gradlew bootRun
#   → http://localhost:8080
#   → http://localhost:8080/actuator/modulith  (Modulith 정보)
```

---

## 11. 지금 어디까지 됐나? (진행 상태)

| Phase | 상태 | 내용 |
|-------|------|------|
| **Phase 0** | ✅ 완료 | 골격 — 4 module + shared + 4 schema + Docker + Modulith verify |
| **Phase 1** | ✅ 완료 | libs 매핑 — order vertical 5모듈(shared/core/batch/adapter/saga) 도메인 + Port |
| **Phase 2** | 🔄 진행 중 | A1 SAGA — sagaStart~step3 + R4 보상 + 토스 구현체(R6) 완료. **step4 confirmedOrder(Pivot) 배선 + @SpringBootTest 통합 검증** 남음 |
| Phase 3 | 진행 예정 | B1 SAGA + `@Externalized` Kafka |
| Phase 4 | 진행 예정 | 3 개 ADR (TIMEOUT / Pivot reconciliation / DLQ) 코드 + ShedLock/scanner |
| Phase 5 | 진행 예정 | Modulith verification + ArchUnit 통합 |

**지금 단계** (2026-06-22): A1 주문 SAGA 의 정상 경로 step1~3 과 보상(R4), 토스 채널 연동까지 코드로 동작합니다(단위 테스트 기준). step4 Pivot 배선과 통합 테스트가 다음 작업입니다.

---

## 12. 더 깊이 보려면

- **MSA 형제 프로젝트** — 같은 기능의 MSA 버전이 `partner-channel-msa` 에 있습니다. 비교하면서 보면 Modulith ↔ MSA 전환 학습에 큰 도움이 됩니다.
- **CLAUDE.md** — 프로젝트 규칙 / 코딩 컨벤션 / 합의된 결정 사항이 모두 정리되어 있습니다.
- **docs/adr/** — 앞으로 ADR (Architecture Decision Record) 가 추가됩니다.

---

## 부록 — 자주 헷갈리는 용어

| 용어 | 한 줄 설명 |
|------|-----------|
| **Modulith** | 1 개의 Spring Boot 안에서 모듈 경계를 그어 둔 구조. MSA 와 모놀리식 사이의 절충안. |
| **SAGA** | 여러 단계의 분산 트랜잭션을 보상 (rollback 대용) 으로 처리하는 패턴. |
| **Pivot** | SAGA 에서 "더 이상 되돌릴 수 없는 지점". 이후 실패는 보상이 아니라 재시도로 해결. |
| **Application Event** | Spring 안의 in-process 이벤트. Modulith 에서는 모듈 간 통신 수단. |
| **`@Externalized`** | Application Event 를 외부 Kafka 로도 발행하라는 표시. |
| **Outbox** | DB 에 이벤트를 같이 저장해두고 별도 워커가 발행하는 패턴. 메시지 유실 방지. |
| **Idempotency** | 같은 요청이 여러 번 와도 결과가 같게 만드는 성질. 외부 API 재시도 대응에 필수. |
