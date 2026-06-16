---
name: doc-first-record
description: Doc-first 산출물 (auto-memory / ADR / docs/worklog) 작성 + MEMORY.md 인덱스 갱신 워크플로우. "이번 결정 메모리에 기록", "메모리 갱신", "ADR 작성", "인덱스 정리", "SUPERSEDED 처리" 요청 시 반드시 사용. end-of-day 스킬과는 역할이 다르다 — 본 스킬은 결정 단위 기록, end-of-day 는 하루 단위 정리.
---

# Doc-first Record — partner-channel-modulith

본 프로젝트의 Doc-first 산출물을 일관되게 작성하기 위한 워크플로우.

## 왜 이 워크플로우인가

본 프로젝트 CLAUDE.md 원칙: **"결정 → 짧은 메모리/ADR → 코드"** + **"한 결정당 1 단위"**. 결정을 묶어서 기록하면 향후 supersede 가 어렵고, MEMORY.md 인덱스가 빠르게 200줄을 넘는다. 본 스킬은 "결정 단위 분해 → 적절한 매체 선택 (memory / ADR / worklog) → 인덱스 한 줄 유지" 순서를 강제한다.

## Step 0 — 기록 매체 선택

| 매체 | 사용 시점 | 위치 |
|------|----------|------|
| **auto-memory** | 결정·합의·진행 상태·feedback — 다음 세션에서 자동 로드 필요 | `/Users/mk/.claude/projects/-Users-mk-repository-mk-partner-channel-modulith/memory/` |
| **ADR** | 큰 흐름 결정 — 대안/트레이드오프 명시가 가치 있는 경우 | `docs/adr/XXXX-{slug}.md` |
| **worklog** | 하루 작업 정리 — `end-of-day` 스킬 호출 | `docs/worklog/YYYY-MM-DD.md` |
| **CLAUDE.md "합의된 결정" 표** | 프로젝트 단위 큰 확정 사항 | `CLAUDE.md` |

선택 기준:
- "다음 세션에서 자동으로 떠올라야 함" → memory
- "왜 이 결정인지 + 대안 비교가 가치 있음" → ADR (memory 도 함께)
- "하루 단위 정리" → worklog (end-of-day 스킬 위임)

## Step 1 — 결정 단위 분해 (memory)

큰 결정을 한 메모리로 묶지 않는다. 예 — "SAGA step 2 + step 3 + 토픽 구조" 가 한 세션에 합의되었다면, 메모리 3개로 분해.

분해 기준:
- 향후 supersede 가능성이 다른 결정은 분리 (예: 토픽 구조 변경 ↔ step 흐름 변경 따로)
- 다른 메모리가 링크할 가능성이 다르면 분리

## Step 2 — auto-memory 본문 작성

타입 결정:

| 타입 | 언제 | 본문 구조 |
|------|------|----------|
| **project** | 결정 / 진행 상태 / 합의 사항 | 결정 + **Why:** + **How to apply:** |
| **feedback** | 사용자가 준 협업 규칙 | 규칙 + **Why:** + **How to apply:** |
| **reference** | 외부 시스템 / 자료 위치 포인터 | 위치 + 용도 |
| **user** | 사용자의 역할 / 선호 / 지식 | 사실 + 어떻게 활용 |

본문 템플릿 (project / feedback 예):
```markdown
---
name: {kebab-case-slug}
description: {한 줄 — 향후 검색 시 관련성 판단용}
metadata:
  type: project | feedback | reference | user
---

{결정 또는 사실}

**Why:** {사유 — 사용자 의도나 과거 사건}

**How to apply:** {언제 / 어디에 적용할지}

관련: [[other-memory-name]]
```

## Step 3 — MEMORY.md 인덱스 갱신

인덱스 규칙:
- `- [Title](file.md) — one-line hook` 형식
- 200줄 이내 유지 (초과 임박 시 사용자에게 정리 제안)
- 본문 절대 포함 금지 (인덱스에는 한 줄 hook 만)
- 카테고리별 정렬: 프로젝트 정체성 → 진행 상태 → 협업 스타일 → 참조 → 산출물 → SUPERSEDED

신규 메모리 추가 시 적절한 섹션 식별 후 한 줄 추가.

## Step 4 — SUPERSEDED 처리

기존 결정을 뒤집을 때:
1. 기존 메모리 본문 상단에 SUPERSEDED 표시:
   ```
   > **SUPERSEDED by [[new-decision]] (YYYY-MM-DD)** — {간단 사유}
   ```
2. 기존 메모리는 삭제하지 않는다 (이력 보존)
3. `MEMORY.md` 의 해당 줄을 "SUPERSEDED" 섹션으로 이동
4. 새 메모리 작성 + 인덱스 추가

## Step 5 — ADR 작성

큰 흐름 결정 (예: 4 app 결정 / SAGA Q1~Q4) 은 ADR 도 작성.

ADR 템플릿 (`docs/adr/XXXX-{slug}.md`):
```markdown
# ADR-XXXX: {제목}

## 배경
{문제 상황}

## 결정
{선택한 옵션}

## 대안
- 옵션 A — {장단점}
- 옵션 B — {장단점}

## 트레이드오프
{양보한 것 / 얻은 것}

## 결과
{적용 후 예상 영향}

## 관련 메모리
- [[memory-name]]
```

번호 (XXXX) 는 `docs/adr/` 의 최신 번호 + 1.

## Step 6 — CLAUDE.md "합의된 결정" 표 갱신

프로젝트 단위 확정 사항 (이름 / 아키텍처 / 언어 등) 은 CLAUDE.md 의 표에도 한 줄 추가. 단, **사용자 합의 후** 갱신.

## Step 7 — 상대 날짜 → 절대 날짜

"내일" / "다음 주" 등 상대 표현은 절대 날짜 (YYYY-MM-DD) 로 변환. 오늘 날짜는 시스템 컨텍스트의 `currentDate` 참조.

## Step 8 — 민감 정보 제거

다음은 기록하지 않는다 (이미 메모리 끝 메모: "회사 식별 정보 / sibling repo 참조 / 외부 작업 메모리는 제거됨"):
- 회사명 / 사번 / 내부 식별자
- sibling repo 경로
- 외부 작업 메모리

요청에 민감 정보 포함 시 사용자에게 제거 동의 요청.

## Step 9 — end-of-day 호출 (세션 마무리)

"/eod" / "오늘 작업 기록" 요청 시 본 스킬 대신 `end-of-day` 스킬을 호출. 두 스킬의 역할:
- **doc-first-record**: 결정 단위 기록 (실시간)
- **end-of-day**: 하루 단위 정리 (memory + worklog 동시)

## 산출물 보고 형식

```
## Doc-first 기록 완료

**메모리**: {새 파일 경로 + 한 줄 요약}
**ADR**: {있을 시 경로 + 번호}
**인덱스 갱신**: MEMORY.md {섹션} 에 한 줄 추가
**SUPERSEDED 처리**: {있을 시 어떤 메모리}
**CLAUDE.md 표 갱신 제안**: {있을 시 — 사용자 합의 요청}
```

## 안티패턴

- 큰 결정을 한 메모리에 묶기 (분해 안 함)
- MEMORY.md 에 본문 직접 작성
- 한 줄 hook 이 200자 초과 (인덱스 가독성 깨짐)
- supersede 시 기존 메모리 삭제 (이력 손실)
- 상대 날짜를 그대로 기록 (시간 지나면 해석 불가)
- 사용자 합의 없이 CLAUDE.md 의 "합의된 결정" 표 갱신
