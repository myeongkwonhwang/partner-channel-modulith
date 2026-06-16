---
name: channel-api-research
description: 외부 채널 (토스쇼핑/쿠팡/네이버/유튜브쇼핑) 공식 API 규격 조사 워크플로우. 공식 문서를 우선 출처로 사용하여 endpoint/schema/상태 전이/rate limit/인증 방식을 정리한다. 추정 금지 — 확인 안 되는 사항은 명시. "토스 API 스펙", "쿠팡 cancel", "네이버 주문 상태", "채널 API 비교" 요청 시 반드시 사용. 코드/도메인 매핑 결정 전에 본 스킬로 규격을 먼저 확정한다.
---

# Channel API Research — partner-channel-modulith

외부 채널 공식 API 규격을 정확하게 정리하기 위한 워크플로우.

## 왜 이 워크플로우인가

본 프로젝트 CLAUDE.md 의 핵심 원칙: **"코드/구조 설계가 외부 API 와 맞닿는 부분은 추정 금지 — 공식 문서로 규격 확인 후 작성"**. 외부 API 는 채널마다 명명 / 상태 전이 / 멱등성 보장이 다르고, 추측으로 매핑하면 운영에서 데이터 손실 또는 보상 실패가 발생한다. 본 스킬은 "공식 출처 → 정확한 schema → 본 프로젝트 매핑 후보" 순서로 진행하여 추측이 끼어들 여지를 줄인다.

## Step 0 — 채널 식별 + 출처 결정

요청을 받으면 다음을 확인한다. 입력 부족 시 사용자에게 묻는다.

| 채널 | 1순위 출처 |
|------|-----------|
| 토스쇼핑 | `mcp__toss-shopping-docs__searchDocumentation` / `getPage` MCP |
| 쿠팡 | 쿠팡 윙 공식 개발자 문서 (WebFetch) |
| 네이버 | 네이버 커머스 API 공식 문서 (WebFetch) |
| 유튜브쇼핑 | YouTube Shopping API 공식 문서 (WebFetch) |

API 버전 명시 — 사용자가 명시하지 않으면 가장 최신 v 또는 본 프로젝트에서 이미 사용 중인 버전을 확인 (`docs/as-is/`, 기존 코드).

## Step 1 — 공식 문서 조회

**토스쇼핑**: 반드시 MCP 도구 우선 사용.
```
mcp__toss-shopping-docs__searchDocumentation(query="<주제 / endpoint / 상태 전이>")
→ 결과에서 가장 관련 있는 page id 선택
mcp__toss-shopping-docs__getPage(pageId="<id>")
```

**그 외 채널**: WebFetch 로 공식 문서 URL 직접 조회. 검색이 필요하면 WebSearch.

조회 실패 시 1회 재시도 → 재실패 시 "공식 출처 접근 불가" 로 보고하고 사용자에게 직접 확인 요청.

## Step 2 — Schema 추출

각 API 에 대해 다음을 모두 정리:

1. **요청** — method / path / headers / path variable / query string / body 필드 표
2. **응답** — 정상 (2xx) / 에러 (4xx, 5xx) / 부분 성공 케이스
3. **상태 전이 규칙** — 가능한 from → to 전이, 조건
4. **인증** — OAuth / API Key / partner-secret / TLS 등
5. **rate limit** — 분당 / 초당 / endpoint 별 제한

확인되지 않은 항목은 빈칸으로 두지 말고 **"공식 문서에서 확인 불가"** 로 명시.

## Step 3 — 출처 명시

모든 사실에 출처를 붙인다.
- 토스: `MCP page: <pageId>` 또는 페이지 제목
- 그 외: 정확한 URL + 조회 시점 (날짜)

## Step 4 — 본 프로젝트 매핑 후보 (제안만)

본 에이전트는 도메인 매핑을 결정하지 않고 **제안** 만 한다. 결정은 `saga-architect` 또는 사용자.

다음 항목을 제안한다:
- 본 프로젝트 도메인 모델 (`StagingOrder`, `Order`, `OrderStatus` 등) 과의 mapping 후보
- 채널 status ↔ 내부 saga state 의 mapping
- 멱등성 처리 후보 (Idempotency-Key 헤더 / 외부 status 기반 dedup)
- 에러 분류 후보 (재시도 가능 / 보상 트리거 / DLQ)

## Step 5 — 산출물 형식

```
### {채널} — {endpoint 또는 주제}

**출처**: {URL / MCP page id}
**API 버전**: {v1 / v2 ...}
**조회 시점**: {YYYY-MM-DD}

#### 요청
| 항목 | 값 |
|------|----|
| Method | ... |
| Path | ... |
| Headers | ... |
| Body | (field 표) |

#### 응답
- 정상 (200 / 201 등)
- 에러 (4xx / 5xx — 코드별)

#### 상태 전이
{from} → {to} : {조건}

#### 인증 / rate limit
- 인증: ...
- rate limit: ... | "공식 문서에서 확인 불가"

#### 본 프로젝트 매핑 제안 (사용자 합의 필요)
- 도메인 mapping: ...
- 멱등성 처리: ...
- 에러 분류: ...
```

## Step 6 — 후속 위임

- SAGA 흐름 설계에 영향이 있는 규격이면 → `saga-architect` 에게 `[channel-spec-analyst → saga-architect]` 메시지로 전달
- 향후 재참조 가치가 있는 규격이면 → `doc-keeper` 에게 메모리 보관 요청 (`memory/project_pcm_{channel}_{topic}.md`)

## 안티패턴

- 출처 없이 "일반적으로는" 으로 채우기
- 한 채널의 규격을 다른 채널에 그대로 적용
- API 버전을 가정으로 채우기 (v1 / v2 명시 누락)
- 도메인 매핑까지 단독으로 결정
- 모순되는 두 출처를 임의로 한쪽 선택

## 자주 쓰는 MCP 호출 예시 (토스)

```
# 주문 상태 변경 endpoint 검색
mcp__toss-shopping-docs__searchDocumentation(query="order status update PUT")

# 특정 페이지 조회
mcp__toss-shopping-docs__getPage(pageId="<id>")
```
