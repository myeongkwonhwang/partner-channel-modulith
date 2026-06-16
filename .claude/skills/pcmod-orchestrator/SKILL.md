---
name: pcmod-orchestrator
description: partner-channel-modulith 프로젝트 작업 진입점. 작업 유형을 분류하여 4명의 전문 에이전트 (modulith-architect / channel-spec-analyst / hexa-clean-reviewer / doc-keeper) + 글로벌 java-spring-expert 에게 위임한다. "SAGA step 설계", "모듈 경계", "Application Event 추가", "토스 API 규격", "코드 리뷰", "결정 기록", "ADR 작성", "검토해줘", "step 추가", "Pivot 검토", "@Externalized 매핑" 등 본 프로젝트 작업 요청 시 반드시 본 스킬로 진입. 또한 "다시 실행", "재실행", "보완", "수정", "이전 결과 기반으로", "결과 개선" 같은 후속 작업 키워드에도 트리거.
---

# pcmod-orchestrator — partner-channel-modulith 작업 진입점

본 프로젝트의 모든 작업 요청을 받아 적합한 전문 에이전트에게 위임하는 진입점 스킬.

## 실행 모드

**기본: 서브 에이전트 패턴** — Doc-first 원칙 + 사용자 페이스 존중에 맞게 한 결정당 1 에이전트씩 순차 호출.

**예외: 에이전트 팀 패턴** — 다음 두 가지 경우에만 `TeamCreate` 로 임시 팀 구성:
- 큰 흐름 재설계 (예: A1 전체 재정의, 모듈 경계 대규모 변경, 새 채널 도입 동시)
- 사용자가 "팀 모드로" 명시 지시

## Phase 0 — 컨텍스트 확인

작업 시작 전 다음을 점검한다.

1. **세션 컨텍스트 확인**: 사용자 메시지에 "다시", "재실행", "이전 결과", "보완", "수정" 등 후속 키워드 포함 여부
2. **메모리 인덱스 확인**:
   - 본 프로젝트: `/Users/mk/.claude/projects/-Users-mk-repository-mk-partner-channel-modulith/memory/MEMORY.md`
   - 참조 (MSA — 기능 동일): `/Users/mk/.claude/projects/-Users-mk-repository-mk-partner-channel-msa/memory/MEMORY.md`
3. **브랜치 상태 확인** (필요 시): `git status` / `git log --oneline -5`

실행 모드 결정:
- 후속 키워드 + 관련 결정 메모리 존재 → **부분 재실행** (해당 에이전트만 재호출, 이전 결정 메모리 참조 지시)
- 신규 작업 → **초기 실행**

## Phase 1 — 작업 분류

사용자 요청을 다음 분류표로 매칭한다.

| 요청 키워드 / 패턴 | 위임 대상 | 사용 스킬 |
|------------------|----------|-----------|
| "SAGA step N", "Pivot", "보상 흐름", "신규 흐름", "Application Event", "모듈 경계", "@Externalized 매핑" | `modulith-architect` | `saga-event-design` |
| "토스 API", "쿠팡 cancel", "네이버 주문", "외부 API 규격", "{채널} 스펙" | `channel-spec-analyst` | `channel-api-research` |
| "리뷰", "검토", "컨벤션 확인", "의존성 방향", "PR 검토", "ArchUnit 위반", "ApplicationModules.verify()" | `hexa-clean-reviewer` | `hexa-arch-review` |
| "결정 기록", "ADR 작성", "MEMORY.md 정리", "SUPERSEDED 처리" | `doc-keeper` | `doc-first-record` |
| 하루 마무리 ("/eod", "오늘 작업 기록") | `doc-keeper` | `end-of-day` (글로벌 스킬) |
| 일반 Java/Spring/Modulith 자문 ("왜 이렇게?", "트랜잭션 격리", "N+1", "JPA 패턴", "Modulith vs MSA 비교") | `java-spring-expert` (글로벌) | (전용 스킬 없음 — 에이전트 자체 응답) |
| 모호함 / 위 분류에 안 맞음 | 사용자에게 분류 확인 요청 | - |

## Phase 2 — 위임 실행

### 서브 에이전트 호출 (기본)

```
Agent(
  description="<3~5단어>",
  subagent_type="general-purpose" (modulith-architect 는 "Plan"),
  model="opus",
  prompt="""
당신은 본 프로젝트의 {agent-name} 입니다.
역할 정의는 `.claude/agents/{agent-name}.md` 를 먼저 읽고 따른다.
사용 스킬: `.claude/skills/{skill-name}/SKILL.md` 의 단계대로 진행.

요청:
{사용자 요청 그대로 + 오케스트레이터가 정리한 컨텍스트}

이전 결과 (있을 시):
{후속 작업이면 이전 결정 메모리 또는 산출물 경로}

MSA 참조 (있을 시):
{본 결정이 MSA 메모리/ADR 의 승계인 경우 출처 표시}
"""
)
```

**중요**: `Agent` 호출 시 반드시 `model="opus"` 명시. 본 프로젝트 작업 품질은 추론 깊이에 직결된다.

### 에이전트 팀 호출 (예외)

다음 도구를 활용 (필요 시 ToolSearch 로 스키마 로딩):
- `TeamCreate` — 임시 팀 구성
- `TaskCreate` — 작업 분해 + 의존성 설정
- `SendMessage` — 팀원 간 통신

팀 사용 후 즉시 `TeamDelete` (다음 작업에 영향 없게).

## Phase 3 — 후속 위임 체인

에이전트 산출물을 검토하여 후속 위임이 필요한지 판단.

| 산출물 | 후속 위임 후보 |
|--------|---------------|
| SAGA step / 모듈 경계 설계 합의 | `doc-keeper` — 메모리 기록 |
| 외부 API 규격 조사 완료 | `modulith-architect` (SAGA 영향 시) + `doc-keeper` (보관 가치 시) |
| 코드 리뷰 BLOCKER 발견 | `java-spring-expert` 또는 사용자 — 수정 진행 |
| 반복 위반 패턴 | `doc-keeper` — feedback memory 후보 등록 |
| ApplicationModules.verify() FAIL | `modulith-architect` — 모듈 경계 재설계 검토 |

후속 위임은 자동 실행하지 않고 사용자에게 제안 후 확인 받음 (Doc-first 원칙 + 사용자 페이스 존중).

## Phase 4 — 결과 종합 + 보고

```
## 작업 완료 — {요청 요약}

**위임**: {에이전트 명} (사용 스킬: {skill 명})

**산출물**:
{에이전트 산출물 요약 또는 그대로}

**MSA 참조** (있을 시): {MSA 메모리/ADR 출처}

**후속 위임 제안** (있을 시):
- {위임 대상} — {사유}
```

사용자가 다음 작업을 명시하기 전까지 자발적으로 "다음 작업" 을 제안하지 않는다 (CLAUDE.md 원칙).

## 에러 핸들링

| 상황 | 처리 |
|------|------|
| 작업 분류 모호 | 사용자에게 분류 확인 (AskUserQuestion) 후 진행 |
| 에이전트 1회 호출 실패 | 1회 재시도 → 재실패 시 사용자에게 보고 + 직접 처리 위임 |
| 메모리 충돌 발견 (기존 결정과 모순) | 진행 중단 + 사용자에게 선택 요청 (기존 유지 / supersede) |
| MSA 메모리와 모순 (Modulith 한정 결정) | 본 프로젝트 결정 우선 + MSA 와의 차이 본문에 명시 |
| MCP 도구 (토스 문서) 실패 | `channel-spec-analyst` 에러 핸들링 따름 (사용자 직접 확인 요청) |
| 외부 시스템 (Kafka / DB) 실 호출 필요 | 본 스킬은 설계/리뷰 전용 — 실행은 직접 수행 또는 별도 스킬 |

## 테스트 시나리오

**정상 흐름**: 사용자 — "step 3 confirmedOrder 의 보상 흐름 추가하고 싶어"
1. Phase 0: 메모리 인덱스 확인 — 본 프로젝트 없음 + MSA `project_pcm_step3_confirmedOrder` 발견. Pivot 임 확인.
2. Phase 1: 분류 → `modulith-architect` (saga-event-design 스킬)
3. Phase 2: Agent 호출 — Pivot step 보상 불가 + MSA 의 reconciliation 패턴 (ADR-0004) 승계 권장 전달
4. 에이전트 산출물: "step 3 = Pivot. reconciliation 패턴 승계 (MSA ADR-0004). Modulith 에서는 PENDING_RECONCILIATION event 로 매핑" 보고
5. Phase 3: `doc-keeper` 위임 제안 — 결정 기록
6. Phase 4: 사용자에게 결과 + 메모리 기록 의사 확인

**에러 흐름**: 사용자 — "모든 step 을 다시 설계"
1. Phase 0: 후속 키워드 ("다시") 감지 + 메모리 인덱스 확인 — 본 프로젝트 초기 / MSA 4 step 완료
2. 분류 모호 — 큰 흐름 재설계는 supersede 위험 + MSA 와 분기 위험
3. 사용자에게 확인: "기존 결정을 supersede 하는 큰 변경입니다. ① 일부 step 만 / ② 전체 재설계 후 SUPERSEDED 처리 / ③ MSA 와 의도적으로 분기" 선택 요청
4. 선택에 따라 분기

## 변경 이력 위치

본 하네스의 변경 이력은 프로젝트 `CLAUDE.md` 의 "하네스: partner-channel-modulith" 섹션 참조.

## 후속 작업 트리거 키워드 (description 보강 근거)

"다시 실행", "재실행", "업데이트", "수정", "보완", "이전 결과 기반", "결과 개선" — 이 키워드들도 본 스킬을 트리거. Phase 0 에서 후속 작업으로 판별.
