# ADR-0001: A1 SAGA — 오케스트레이션 vs 코레오그래피

- **Status**: Accepted (2026-06-23, 사용자 합의)
- **관계**: MSA `Q1 오케 일관` 을 **재확인 (supersede 아님)**, 단 적용 범위를 명문화하여 한정.

## 배경

본 프로젝트는 A1 주문 흐름을 in-VM `@ApplicationModuleListener` command/reply 오케스트레이션 (R2) 으로 배선해 왔다. 2026-06-23 사용자가 **"코레오그래피로도 충분히 가능하지 않냐"** 를 정당하게 지적. MSA 의 "오케 일관" 결정을 권위로 그대로 답습하지 않고, **현재 코드 (R2~R5a) 위에서 코레오 대안을 코드 레벨로 재평가** 한다.

코레오그래피 = 각 모듈이 도메인 이벤트를 발행하고, 다음 단계가 그것을 구독하여 반응 (중앙 조정자 없음). 오케스트레이션 = saga 오케가 command/reply 로 흐름을 명시적으로 지휘.

## 핵심 가설과 검증

**가설: "코레오는 saga-log 를 없앤다."** → **검증 결과 = 거짓 (코드로 확정).**

- R5a `SagaTimeoutScanner` 는 `(correlationKey, current_step, last_transition_at, status)` 를 요구한다 — "이 주문이 지금 어느 단계에서 얼마나 멈춰있나" 를 알아야 timeout/stuck 을 잡는다.
- 순수 코레오에는 이 상태를 담을 곳이 없다. **"다음 이벤트의 부재"** 는 어떤 listener 도 트리거하지 않는다 = **구조적 맹점** (멈춤을 감지할 주체가 없음).
- timeout 을 구현하려면 둘 중 하나가 강제된다:
  1. 모듈 간 직접 조회 (각 모듈 상태를 스캐너가 읽음) → `ApplicationModules.verify()` 위반.
  2. 모든 도메인 이벤트를 구독하는 **중앙 관찰자 테이블** → 이것은 `saga_state` 와 동형 (isomorphic).
- ∴ 코레오는 중앙 상태를 **없애지 못한다**. 단지 **결정 권한 (누가 다음을 지시하나)** 만 제거한 채 **관찰 책임** 은 그대로 남긴다 → **"흐름은 분산 + 상태는 중앙화"** 라는 최악의 조합.

## 결정

**A1 = 오케스트레이션 유지 (재확인).** 근거:

- **복구·운영 요구가 saga-log 를 강제** — R4 `PENDING_MANUAL_CANCEL`, R5a timeout/reconciliation/DLQ 가 전부 중앙 saga 상태를 전제.
- **전역 불변식이 강함** — Pivot (step4 이후 보상 금지), 보상 LIFO 순서, 외부→내부 순서. 이것을 한 곳에서 강제할 수 있어야 한다.
- **외부 부수효과의 순서가 중요** — seller-cancel → staging 취소 같은 보상 LIFO 는 분산 반응으로 창발시키면 순환·경쟁 위험.

## 대안

- **옵션 A — 오케스트레이션 (채택)**: command/reply 14 record. saga_state 단일 스캔으로 복구. 보상 LIFO 가 한 메서드에 명시. Pivot 불변식이 한 곳에서 컴파일/런타임 강제. 비용 = event record 수가 많아 보임.
- **옵션 B — 코레오그래피 (기각)**: 단방향 ~8 이벤트 (겉보기 적음). 그러나 보상이 **창발** (순환 위험), Pivot 이 **"전역 규율"** 로만 존재 (컴파일러가 못 잡음), step 순서 변경 시 다중 모듈 동시 수정. 그리고 위 검증대로 **중앙 관찰자 테이블을 결국 재도입**.
- **공통 (이점 0)**: 멱등 (`processed_event`) · Tx 분리 (외부 호출 Tx 밖) 는 양쪽 동일하게 필요 — 코레오를 택해도 이 비용은 줄지 않는다.

## 트레이드오프

- **얻은 것**: 한 곳 (saga 오케 + saga_state) 에서 흐름·복구·불변식을 통제. 운영 도구 (scanner/reconciliation/DLQ) 가 단일 상태원을 본다.
- **양보한 것**: event record 수가 많고, 새 step 추가 시 오케 코드를 건드려야 함 (단, 흐름이 한 곳에 모이는 게 오히려 가독성 이득).

## 반대 선택이 옳은 조건 — "오케를 전역 기본값으로 박지 않는다"

전역 불변식이 약하고 복구 요구가 없는 **fan-out 반응** 은 코레오가 정답이다 (중앙 상태 불요).

- CLAUDE.md Phase1+ 의 **notification / analytics 모듈은 코레오로 구독** 해야 한다. 이들은 "주문이 확정됐다" 같은 사실에 반응만 하고, 보상도 전역 불변식도 없다.
- 즉 본 ADR 은 **"전역 불변식·복구가 있는 SAGA"** 에만 오케를 한정한다.

## 실무 냉정 판단 (Decision 보강)

핵심 트랜잭션 (주문/결제/재고 = 보상·외부효과 있음) 은 실무에서 **오케가 맞다, 접전이 아니다.** 결정 요인 4:

1. **관측성** — "이 주문 지금 어디" 를 한눈에. (코레오는 전체 흐름을 안 보이게 함)
2. **보상 정확성** — 분산 보상은 버그 서식지.
3. **변경 속도** — 흐름 변경이 한 곳.
4. **장애 대응** — 단일 상태원에서 복구.

업계 진자는 이미 **"saga = 오케, 이벤트 = 코레오"** 로 회귀 (복잡 흐름을 전부 코레오한 팀은 대부분 후회하고 process manager 를 도입). **모놀리스 한정 결정타**: 코레오의 핵심 이점 (팀/배포 자율) 이 단일 배포물에서는 거의 무효 → 모놀리스의 핵심 saga 는 오케가 더 결정적. fan-out 반응 (notification 등) 만 코레오.

## 결과 (Consequences)

- **코드 무변경.** `ApplicationModules.verify()` / ArchUnit 무영향.
- "오케 일관" 의 적용 범위를 **"전역 불변식·복구가 있는 SAGA"** 로 한정 명문화. notification/analytics 는 명시적으로 코레오 권장.
- 본 결정은 더 근본적인 추론 (이 saga 가 애초에 필요한가 / modulith vs MSA / 이상적 재설계) 의 출발점이며, 그 전체 맥락은 메모리 `project_pcmod_architecture_reasoning_2026_06_23` 에 박제.

## 관련 메모리

- [[project-pcmod-architecture-reasoning-2026-06-23]] — 본 ADR 을 포함하는 4단 추론 + 이상적 재설계
- [[project-pcmod-r2-external-state-transition]] — A1 step 구조 + in-VM 오케 트리거
- [[project-pcmod-r4-compensation]] — 보상 LIFO + PENDING_MANUAL_CANCEL
- [[project-pcmod-r5a-operations]] — SagaTimeoutScanner (saga-log 요구 근거)
