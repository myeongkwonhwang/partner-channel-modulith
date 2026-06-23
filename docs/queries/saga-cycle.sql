-- =============================================================================
-- saga-cycle.sql — A1 SAGA 한 사이클(주문 1건) 추적 쿼리
-- =============================================================================
-- 한 주문이 여러 schema 에 남긴 흔적(saga_state / staging_order / processed_event /
-- event_publication)을 correlation_key 하나로 모아 시간순으로 본다.
--
-- 사용법 (psql / DataGrip):
--   1) correlation_key 찾기:
--        SELECT correlation_key, current_step, status
--        FROM saga_schema.saga_state ORDER BY started_at DESC LIMIT 10;
--   2) 아래 \set 의 값을 바꾼 뒤 쿼리 실행.
--
-- 주의(타임존): event_publication.publication_date 만 timestamptz 이고 나머지 컬럼은
--   naive KST 라, publication_date 를 AT TIME ZONE 'Asia/Seoul' 로 정규화해야 정렬이 맞다.
--
-- 읽는 법:
--   - outbox [PENDING] = 해피패스의 ConfirmedOrderCommand(step4 미배선 dangling)와
--     @Externalized Kafka 발행이 미완으로 남는 정상 상태.
--   - 보상 사이클(fail)이면 compensate*/​*Compensated 마크 + staging status=CANCELED +
--     saga_state status=COMPENSATED 가 추가로 보인다.
-- =============================================================================


-- ── [1] 한 사이클 타임라인 ────────────────────────────────────────────────────
-- ↓ 보고 싶은 주문의 correlation_key 로 교체. (주의: psql \set 은 줄 끝까지를 값으로
--   먹으므로 같은 줄에 -- 주석을 달지 말 것 — 변수값이 오염된다.)
\set ck 'NAVER:DEV-1782181090048'

WITH p AS (
  SELECT correlation_key AS ck, saga_id::text AS sid,
         split_part(correlation_key, ':', 2) AS ext
  FROM saga_schema.saga_state WHERE correlation_key = :'ck'
)
SELECT to_char(t.ts, 'HH24:MI:SS.MS') AS at, t.source, t.stage, t.detail
FROM p CROSS JOIN LATERAL (
    -- saga 인스턴스 최종 상태
    SELECT s.last_transition_at ts, 'saga_state' source,
           s.current_step||' / '||s.status stage, 'reconcile='||s.reconciliation_attempts detail
    FROM saga_schema.saga_state s WHERE s.correlation_key = p.ck
  UNION ALL
    -- staging 최종 상태 (보상 시 CANCELED)
    SELECT o.updated_at, 'staging_order', 'status='||o.status, 'id='||o.id
    FROM channel_schema.staging_order o
    WHERE o.channel||':'||o.external_order_product_id = p.ck
  UNION ALL
    -- saga 멱등 마크 (sagaStart / *Reply / compensate*)
    SELECT pe.processed_at, 'saga.idempotency', pe.consumer_name, 'mark'
    FROM saga_schema.processed_event pe WHERE pe.event_id = p.ck
  UNION ALL
    -- adapter(channel) 멱등 마크 (unconfirmedOrder / channelConfirm / compensate:*)
    SELECT pe.processed_at, 'channel.idempotency', pe.consumer_name, 'mark'
    FROM channel_schema.processed_event pe WHERE pe.event_id = p.ck
  UNION ALL
    -- 내장 outbox 발행 (event_type + 소비 핸들러, 미완=PENDING). 시각은 KST 정규화
    SELECT (ep.publication_date AT TIME ZONE 'Asia/Seoul'), 'outbox',
           regexp_replace(ep.event_type, '.*\.', '')
             || CASE WHEN ep.completion_date IS NULL THEN ' [PENDING]' ELSE '' END,
           regexp_replace(ep.listener_id, '^.*\.([A-Za-z0-9_]+)\.[A-Za-z0-9_]+\(.*$', '\1')
    FROM events_schema.event_publication ep
    WHERE ep.serialized_event LIKE '%'||p.sid||'%' OR ep.serialized_event LIKE '%'||p.ext||'%'
) t
ORDER BY t.ts;


-- ── [2] 한 줄 요약 (빠른 점검) ────────────────────────────────────────────────
-- outbox_pending_total = 미완/전체 발행 수.
SELECT
  (SELECT current_step||'/'||status FROM saga_schema.saga_state WHERE correlation_key=:'ck') AS saga,
  (SELECT status FROM channel_schema.staging_order
     WHERE channel||':'||external_order_product_id=:'ck') AS staging,
  (SELECT count(*) FROM saga_schema.processed_event   WHERE event_id=:'ck') AS saga_marks,
  (SELECT count(*) FROM channel_schema.processed_event WHERE event_id=:'ck') AS channel_marks,
  (SELECT count(*) FILTER (WHERE completion_date IS NULL) || '/' || count(*)
     FROM events_schema.event_publication
     WHERE serialized_event LIKE '%'||(SELECT saga_id FROM saga_schema.saga_state WHERE correlation_key=:'ck')||'%'
   ) AS outbox_pending_total;
