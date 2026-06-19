---
name: test-write
description: 본 프로젝트(partner-channel-modulith) 테스트 작성 워크플로우. 헥사 계층별 전략(domain 순수 단위 / application Port mock / infra @DataJpaTest+Testcontainers 슬라이스) + 멱등 native ON CONFLICT 원자성·동시성 race 검증 + @Table(schema=) 라우팅·복합 PK·mapper 왕복 + Spring Modulith verify/@ApplicationModuleTest + ArchUnit D-3 규칙을 단계적으로 작성한다. "테스트 작성", "테스트 추가", "단위 테스트", "슬라이스 테스트", "동시성 검증", "멱등 테스트", "ArchUnit 규칙", "테스트 보강", "다시 테스트", "테스트 수정" 요청 시 반드시 사용. 코드 작성 직후 테스트가 필요하면 본 스킬로 진입한다.
---

# Test Write — partner-channel-modulith

본 프로젝트의 테스트를 **헥사 계층별로** 작성하는 워크플로우. 계층이 테스트 종류와 격리 수준을 결정한다.

## 왜 이 워크플로우인가

본 프로젝트의 핵심 불변식들 — 멱등 native `ON CONFLICT` 의 **원자성**, `@Table(schema=)` 라우팅, 외부+DB Tx 분리, 모듈 경계 — 은 일반 단위 테스트로는 안 잡힌다. 멱등 원자성은 동시 호출을 실제로 일으켜야 하고, schema 라우팅·복합 PK·`ON CONFLICT` 의미는 H2 와 PostgreSQL 이 달라 Testcontainers 가 필요하다. 본 스킬은 계층마다 올바른 테스트 종류를 강제해, "verify PASS = 검증됐다" 는 착시(정적 분석일 뿐 도메인/영속 단위 미검증)를 막는다.

## Step 0 — 범위·깊이 확정

| 입력 | 예 |
|------|----|
| 대상 범위 | 단일 파일 / 디렉토리 / 모듈(batch.ordr 등) / git diff |
| 깊이 | 빠른(순수 단위만) / 표준(+슬라이스) / 깊은(+동시성·ArchUnit·Modulith) |

입력 부족 시 사용자에게 직접 묻는다. 이전 테스트가 있으면 읽고 **누락 케이스만 보강**(중복 작성 금지).

## Step 1 — 계층 분류

대상 클래스를 패키지로 계층 판정한다. 계층이 곧 테스트 종류다.

| 패키지 | 계층 | 테스트 종류 |
|--------|------|------------|
| `..domain..` (record/port) | domain | 순수 JUnit5 (프레임워크 0) |
| `..application..` | application | Port mock (Mockito) |
| `..infra.persistence..` | infra | `@DataJpaTest` + Testcontainers |
| `..infra.idempotency..` | infra | `@DataJpaTest` + Testcontainers + 동시성 |
| 루트 modulith | modulith | `verify()` / `@ApplicationModuleTest` |
| 구조 규칙 | arch | ArchUnit |

> Port 인터페이스 자체(`*Repository`, `*Strategy`)는 단위 테스트 대상이 아니다 — 구현(Adapter)을 슬라이스로 검증한다.

## Step 2 — domain 순수 단위

프레임워크 없이 record 의 계약을 단언한다.

- **`requireNonNull` 가드** — 각 필수 필드 null 에 `NullPointerException` (`assertThatThrownBy`)
- **정적 팩토리** — `of`/`from`/`initial`/`newRecord` 가 기대 필드·기본 상태(예: `status="CREATED"`)를 만드는지
- **불변 전이** — `advanceTo`/`with*` 가 **새 인스턴스**를 반환하고 원본 불변인지, 변경 필드만 바뀌는지
- **키 빌더** — `EventKey.of(channel, id)` 가 `"{code}:{id}"` 형식인지, null 가드

테스트 클래스: `{Type}Test`, `@Test` + AAA. Spring/JPA import 금지.

## Step 3 — application Port mock

Port 를 Mockito 로 stub 하여 흐름·분기만 검증(DB·외부 없음). Phase 1 골격은 application 로직이 거의 없으므로(`Validator` 의 판매룰은 Phase 2 TODO), 흐름이 비면 **건너뛰고 보고**한다. 무의미한 mock 단언을 만들지 않는다.

## Step 4 — infra 슬라이스 (@DataJpaTest + Testcontainers)

실제 PostgreSQL 로 영속 계약을 검증한다.

### 4-1. Testcontainers 기반 셋업

`build.gradle` 에 의존이 없으면 추가를 안내하고 승인 후 반영:

```groovy
testImplementation 'org.springframework.boot:spring-boot-testcontainers'
testImplementation 'org.testcontainers:junit-jupiter'
testImplementation 'org.testcontainers:postgresql'
```

공통 베이스(컨테이너 1개 재사용 + schema/Flyway 적용):

```java
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE) // 실제 PG 사용
@Testcontainers
abstract class PersistenceSliceTest {
    @Container @ServiceConnection
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16");
    // Flyway 가 4 schema baseline 적용 → @Table(schema=) 가 실제 매핑되는지 검증 가능
}
```

> H2 금지 — schema 네임스페이스·`ON CONFLICT`·복합 PK 의미가 PostgreSQL 과 달라 본 프로젝트 불변식을 못 잡는다. `@ServiceConnection`(Boot 3.1+) 으로 DataSource 자동 연결.

### 4-2. persistence 검증 케이스

- **mapper 왕복** — `domain → from() → @Entity → save → toDomain()` 후 필드 동등(시각 포함). id 채번 확인.
- **schema 라우팅** — 저장된 row 가 의도한 schema(`core_schema.orders` / `channel_schema.polling_cursor`)에 들어갔는지 (native count 또는 조회).
- **UNIQUE 제약** — `(channel, externalOrderProductId)` 중복 save 시 `DataIntegrityViolationException`.
- **조회 Port** — `findByChannelAndExternalOrderProductId` 존재/부재(`Optional.empty`) 양쪽.

## Step 5 — 멱등 원자성·동시성 (최우선)

`markIfFirst` 는 본 프로젝트에서 가장 중요한 불변식이다. 단일 스레드 + 동시성 둘 다 작성한다.

- **첫 호출 true / 중복 false** — 같은 `(consumerName, eventId)` 두 번째 호출이 `false`.
- **서로 다른 키 독립** — 다른 eventId 는 서로 영향 없음.
- **동시성 race (핵심)** — N 스레드가 같은 키로 동시에 `markIfFirst` 호출 시 **정확히 1개만 true**:

```java
int N = 20;
var latch = new CountDownLatch(1);
var pool = Executors.newFixedThreadPool(N);
var trueCount = new AtomicInteger();
var done = new CountDownLatch(N);
for (int i = 0; i < N; i++) {
    pool.submit(() -> {
        latch.await();                       // 동시 출발
        if (repo.markIfFirst("orderReception", "TOSS:o-1")) trueCount.incrementAndGet();
        done.countDown();
        return null;
    });
}
latch.countDown();
done.await();
assertThat(trueCount.get()).isEqualTo(1);    // ON CONFLICT DO NOTHING 원자성
```

> 동시성 테스트는 `markIfFirst` 가 각자 Tx 로 커밋되도록 `TransactionTemplate(REQUIRES_NEW)` 로 호출한다(`@DataJpaTest` 기본 롤백 Tx 안에서 그냥 호출하면 race 가 재현 안 됨). race 재현이 환경상 불안정하면 그 사유를 보고한다.

> **함정 — ambient Tx + TRUNCATE 자기-교착 (실측, 무한 행)**: 멱등 슬라이스에서 `@BeforeEach` 의 `TRUNCATE`(또는 DELETE)를 `@DataJpaTest` 의 메서드 롤백 Tx 안에서 돌리면, 그 Tx 가 테이블에 `ACCESS EXCLUSIVE` 락을 잡고 테스트 종료까지 유지한다. 본문이 `REQUIRES_NEW` 로 ambient Tx 를 suspend 한 채 **다른 커넥션**에서 `INSERT`(RowExclusive)를 시도하면 같은 테이블 락을 기다리며 **무한 대기**(lock_timeout 없음 → 테스트가 영원히 안 끝남, `done.await` 타임아웃과 무관). **해법: 멱등/커밋형 슬라이스 클래스에 `@Transactional(propagation = Propagation.NOT_SUPPORTED)` 를 붙여 ambient Tx 를 끈다** — 그러면 TRUNCATE 가 즉시 커밋돼 락을 안 잡고, REQUIRES_NEW INSERT 도 자유롭게 커밋된다. 증상이 "FAIL" 이 아니라 "결과 XML 자체가 안 생기는 행" 이면 이 패턴을 먼저 의심하고 `jstack` 으로 INSERT 가 소켓 read 에 멈췄는지 확인한다.

## Step 6 — Modulith / ArchUnit

- **`ApplicationModules.verify()`** — 이미 `ModulithApplicationTests` 에 존재. 모듈 추가 시 통과 확인만.
- **`@ApplicationModuleTest`** (Phase 2~) — 이벤트 발행·`@ApplicationModuleListener` 수신·`PublishedEvents`/`AssertablePublishedEvents` 단언. listener 미구현(Phase 1)이면 보류.
- **ArchUnit D-3** (도입 가능):
  - `@Entity` 는 `..infra..` 패키지에만
  - `..domain..` 은 `jakarta.persistence..` import 금지
  - `@Entity` 클래스는 `@Table(schema)` 필수(schema 속성 비어있지 않음)

## Step 7 — 실행·보고

`./gradlew test` (또는 `--tests` 필터) 실행. 결과를 출력 프로토콜로 보고:

```
## 테스트 작성 — {범위}
### 작성한 테스트   : `{파일}` — {계층}/{종류} : {케이스}
### 빌드·실행 결과  : ./gradlew test PASS/FAIL + build.gradle 변경 여부
### 커버 못한 영역  : {사유}
```

## 컨벤션 (본 프로젝트)

- 빌드 스택 Java 25 + Boot 4.0.7 + Modulith 2.0.7 + JUnit5 + AssertJ + archunit 1.4.2 (`spring-modulith-starter-test` 포함됨)
- 테스트도 `log.info` 단일, 식별 정보 메시지 본문에
- 시간/랜덤은 경계로 — 결정적 테스트
- 표면 중복 슬라이스는 무리하게 통합하지 않음(의도 드러나면 분리 유지)
- 외부 호출은 프로덕션처럼 Tx 밖 — 슬라이스 테스트는 외부 mock, DB 만 실제

## 테스트 시나리오

**정상**: "batch.ordr 테스트 작성해줘"
1. Step 0 범위=batch.ordr 모듈 / 깊이=표준+동시성
2. Step 1 계층 분류: `PollingCursor`(domain) / `PollingCursorRepositoryAdapter`(infra persistence) / `ChannelProcessedEventRepositoryAdapter`(infra idempotency)
3. Step 2 `PollingCursorTest`(requireNonNull/initial/advanceTo 불변) → Step 4 `PollingCursorRepositoryAdapterTest`(Testcontainers, channel_schema 라우팅, UNIQUE) → Step 5 `ChannelProcessedEventRepositoryAdapterTest`(첫 true/중복 false + 20스레드 race 1건)
4. Step 7 `./gradlew test` 실행·보고

**에러**: "멱등 동시성 테스트 추가" 인데 Testcontainers 의존 없음
1. Step 4-1 에서 의존 부재 감지 → build.gradle 추가 안내 + 승인 요청
2. Docker 미가동이면 테스트는 작성하되 "Docker 필요" 명시, race 재현 불안정 시 사유 보고
