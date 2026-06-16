---
name: hexa-arch-review
description: 헥사고날 + Clean Architecture + Effective Java + 본 프로젝트 컨벤션 + Spring Modulith 모듈 경계 (log.info / 도메인 예외 계층 / Optional / @ApplicationModule allowedDependencies / ApplicationModules.verify()) 코드 리뷰 워크플로우. 신규/수정 코드, PR diff, 브랜치 검토 시 반드시 사용. "리뷰", "검토", "컨벤션 확인", "의존성 방향 점검", "ArchUnit 위반", "Modulith verify" 요청 시 본 스킬을 호출. 모든 위반은 BLOCKER/SUGGESTION 으로 분리하여 보고한다.
---

# Hexa Clean Review — partner-channel-modulith

본 프로젝트의 헥사고날 + Clean Architecture + Effective Java + 프로젝트 컨벤션 + Spring Modulith 모듈 경계를 다층으로 점검하는 워크플로우.

## 왜 이 워크플로우인가

본 프로젝트는 세 개의 강한 규칙 (CLAUDE.md "코딩 규칙" + Modulith 의존 규칙 + `ApplicationModules.verify()`) 을 가지고 있고, ArchUnit 강제 예정이다. 리뷰가 일관되지 않으면 `verify()` 도입 시 한꺼번에 깨지는 코드가 누적된다. 본 스킬은 검토를 **객관 규칙 (의존 방향 / 모듈 경계 / 매직 스트링)** 과 **컨벤션 (log / 예외 / Optional / 외부+DB Tx)** 두 층으로 분리하여, 위반 종류를 명확히 분류하도록 강제한다.

## Step 0 — 검토 범위 확정

다음 입력을 명확히 한다.

| 입력 | 예 |
|------|----|
| 검토 범위 | 단일 파일 / 디렉토리 / `git diff` / 브랜치 (`feature/*`) |
| 검토 깊이 | 빠른 (BLOCKER 만) / 표준 (BLOCKER + SUGGESTION) / 깊은 (모든 항목 + 패턴 제안) |

입력 부족 시 사용자에게 직접 묻는다.

## Step 1 — 객관 규칙 검토 (BLOCKER 후보)

### 1-1. 의존성 방향 (Clean Architecture)

- Domain → Spring / JPA / Kafka 의존 금지 (Lombok 만 허용)
- Port 인터페이스는 Domain 패키지에, 구현은 Infrastructure 패키지에
- Application 은 Domain 에 의존, Infrastructure 는 Application 또는 Domain 에 의존, 역방향 금지
- JPA Entity 와 Domain Entity 혼용 금지 (별도 클래스 + mapper)

**위반 감지 방법**: import 문 scan. 예 — `domain/` 패키지 안에서 `import org.springframework`, `import javax.persistence` 발견 시 BLOCKER.

### 1-2. Spring Modulith 모듈 경계

- 각 module (`batch` / `adapter` / `core` / `saga`) → `shared` 만 의존 (`@ApplicationModule(allowedDependencies = { "shared" })`)
- module 간 직접 호출 (`@Autowired`, static call, public class import) 발견 시 BLOCKER — Application Events 만
- `@ApplicationModuleListener` 가 비동기인지 확인 (동기 처리 시 SUGGESTION)
- `internal/` 패키지 클래스가 다른 module 에 노출 시 BLOCKER
- `@Externalized` 가 외부 트리거 event 에만 적용 — 내부 event 가 외부 누출 시 BLOCKER

**위반 감지 방법**:
- `ApplicationModules.verify()` 테스트 실행 (`ModulithApplicationTests`)
- import 문 scan: `batch/` 안에서 `import ...adapter.`, `...core.`, `...saga.` 발견 시 BLOCKER (반대 방향 동일)
- `@Externalized` annotation 의 적용 대상 검토

### 1-3. 외부 API + DB Tx 위반

**원칙**: 외부 API 호출은 `@Transactional` 밖에서. 외부가 WRITE 면 응답 후 별도 Tx 에서 DB 반영.

**위반 패턴 감지**:
- `@Transactional` 메서드 안에서 RestTemplate / WebClient / Feign / `@FeignClient` 호출 발견
- 동일 메서드에서 외부 호출 + DB write (JPA save, Mapper insert) 가 같은 Tx 안

**즉시 BLOCKER**.

### 1-4. 매직 스트링

- 채널 식별자는 `Channel` enum 사용 — 문자열 "TOSS" / "COUPANG" 직접 사용 시 BLOCKER
- 토픽명 / header 값은 Constants 클래스 또는 enum

### 1-5. `get(0)` / `Optional.get()` 무방어 호출

- `list.get(0)` → 방어 메서드 (`findFirstOrNull` 등) 사용 권장
- `Optional.get()` → `orElseThrow(...)` 또는 `orElse(...)` 명시

## Step 2 — 컨벤션 검토 (BLOCKER / SUGGESTION)

### 2-1. 예외 계층 (BLOCKER)

본 프로젝트는 도메인별 unchecked 계층:
- 베이스: `PartnerChannelException` (`shared/` 모듈)
- abstract sub: `SagaException`, `ChannelException`, `LogisticsException` 등 도메인별
- final 구체: `SagaPublishException`, `LogisticsDispatchException` 등

**위반 감지**:
- `RuntimeException` / `IllegalStateException` 등을 직접 throw 시 BLOCKER (도메인 예외 사용 권장)
- 단일 추상 베이스 (`BizRuntimeException` 류) 도입 시도 발견 → BLOCKER ([[feedback_exception_policy]] 위반)
- 예외 메시지에 식별 정보 (sagaId / orderId / messageId) 없음 → SUGGESTION

### 2-2. 로깅 (BLOCKER)

- `log.info` 만 사용. `log.debug` / `log.warn` / `log.error` / `log.trace` 발견 시 BLOCKER (예외 처리 catch 블록 포함)
- 식별 정보 (sagaId 등) 메시지 본문에 명시되지 않음 → SUGGESTION
- 로깅 보조 추상화 (`SkipReason` enum / `Result` 타입) 발견 → SUGGESTION (도입 신중성 알림)

### 2-3. Spring (BLOCKER + SUGGESTION)

- Controller 안에 비즈니스 로직 (조건문 + DB 쓰기 등) → BLOCKER
- `@Transactional` 범위가 메서드 전체 대신 좁은 블록으로 분리 가능 → SUGGESTION
- DTO ↔ Entity 직접 노출 (Controller 가 JPA Entity 반환) → BLOCKER

### 2-4. Effective Java (대부분 SUGGESTION)

- 정적 팩토리 (`of()` / `from()`) 적용 가능한 곳에 public 생성자 사용 → SUGGESTION
- final 필드 / `List.of()` 등 불변성 적용 가능한 곳 → SUGGESTION
- Port 타입 대신 구현체 타입으로 변수 선언 → SUGGESTION

## Step 3 — 산출물 작성

```
## 리뷰 결과 — {범위}
검토 깊이: {빠른/표준/깊은}

### BLOCKER ({n}건)
1. `{파일:라인}` — {위반 항목 한 줄}
   - **위반 이유**: {1~2줄}
   - **수정 제안**:
     ```java
     // 현재
     ...
     // 제안
     ...
     ```

### SUGGESTION ({n}건)
- `{파일:라인}` — {개선 + 이유 1줄}

### Modulith verify() 결과
- ApplicationModules.verify() PASS / FAIL (FAIL 시 위반 표시)

### CLEAN
- {위반 없는 영역 짧게 — 사용자가 검토 범위를 알 수 있게}
```

## Step 4 — 후속 위임

- 의존성 방향 / 모듈 경계 위반이 SAGA 설계 결함으로 보이면 → `modulith-architect` 에게 설계 재검토 요청
- 반복되는 위반 패턴 (3 회 이상 동일 위반) 발견 → `doc-keeper` 에게 feedback memory 후보 제보:
  ```
  [hexa-clean-reviewer → doc-keeper] feedback memory 후보
  패턴: {반복 위반}
  사유: ...
  How to apply: ...
  ```

## Step 5 — 코드 직접 수정 금지

본 스킬은 검토만 수행한다. 수정은 사용자 또는 `java-spring-expert` 가 담당. 본 스킬의 "수정 제안" 은 **샘플 코드** 일 뿐.

## 안티패턴 (감지 + 보고)

- `@Transactional` 안에서 외부 API 호출 → 즉시 BLOCKER
- 단일 추상 예외 베이스 도입 시도 → BLOCKER ([[feedback_exception_policy]])
- log.warn / log.error 사용 → BLOCKER
- `Channel` enum 대신 문자열 "TOSS" 직접 사용 → BLOCKER
- 표면 중복 코드의 무리한 통합 → SUGGESTION ([[feedback_clarify_over_consolidate]] — 통합보다 명문화)

## 자주 쓰는 명령

```
# 브랜치 diff 검토
git diff main...HEAD --stat

# import 위반 검색 (Domain 안에 Spring/JPA)
grep -rn "import org.springframework\|import jakarta.persistence" src/main/java/.../domain

# module 간 직접 의존 위반 검색
grep -rn "import io.github.orange2652.partner.channel.adapter\." src/main/java/io/github/orange2652/partner/channel/core
grep -rn "import io.github.orange2652.partner.channel.core\." src/main/java/io/github/orange2652/partner/channel/adapter

# @Transactional + 외부 호출 동시 발견
grep -rn "@Transactional" src/main/java | xargs -I {} ...

# Modulith verify() 빠른 실행
./gradlew test --tests "ModulithApplicationTests"
```

스크립트화가 필요한 패턴이 누적되면 본 스킬의 `scripts/` 아래에 번들링 (현재는 미작성).
