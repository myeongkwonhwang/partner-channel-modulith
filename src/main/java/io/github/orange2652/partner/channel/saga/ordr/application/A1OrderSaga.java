package io.github.orange2652.partner.channel.saga.ordr.application;

/**
 * A1(주문) saga 의 식별 상수 — saga_state 의 {@code saga_type} / {@code current_step} 매직 스트링을 한 곳에 모은다
 * (CLAUDE.md 매직스트링 금지). step 진행 규칙(어느 step 으로 언제 advance)은 각 step listener 가 갖고, 본 클래스는
 * "이름"만 보유한다.
 *
 * <p>현재는 sagaStart 가 쓰는 {@link #TYPE} / {@link #STEP_UNCONFIRMED_ORDER} 만 둔다. 나머지 step 상수는 해당
 * step listener 를 와이어링할 때 추가한다(투기적 선언 자제). reconciliation step 명은 saga V1 인덱스
 * {@code CONFIRMED_ORDER_PENDING_RECONCILIATION} 와 정합시킨다.</p>
 */
public final class A1OrderSaga {

    /** {@code saga_state.saga_type} — A1(주문) saga. */
    public static final String TYPE = "A1_ORDER";

    /** 시작 step — sagaStart 직후. 외부 raw 의 미확정 staging 적재(R2 step1) 대기. */
    public static final String STEP_UNCONFIRMED_ORDER = "UNCONFIRMED_ORDER";

    /** step1 완료 후 — 판매가능 검증(R2 step2, core, read-only) 대기. */
    public static final String STEP_VALIDATE = "VALIDATE";

    /** step2 통과 후 — 외부 채널 수락 통보(R2 step3, adapter, 외부 Tx 밖) 대기. */
    public static final String STEP_CHANNEL_CONFIRM = "CHANNEL_CONFIRM";

    /** step3 통과 후 — 내부 주문 확정 + 자사 물류 전송(R2 step4 Pivot, core) 대기. */
    public static final String STEP_CONFIRMED_ORDER = "CONFIRMED_ORDER";

    /** 시작/진행 중 status — stuck scanner(R5a)가 SLA 초과 RUNNING 을 조회하는 키. SagaState.start 의 초기 status. */
    public static final String STATUS_RUNNING = "RUNNING";

    /** 보상 진행 중 (R4) — 외부 cancel → staging 취소 LIFO 진행. saga_state.status. */
    public static final String STATUS_COMPENSATING = "COMPENSATING";

    /** 보상 완료 terminal (R4) — 외부 cancel + staging 취소 모두 성공. */
    public static final String STATUS_COMPENSATED = "COMPENSATED";

    /** 외부 cancel 미가용/영구실패 terminal (R4) — 운영자 수동 개입 대기. */
    public static final String STATUS_PENDING_MANUAL_CANCEL = "PENDING_MANUAL_CANCEL";

    /** Pivot(step4) stuck (R5a ③) — 주문 확정/물류 전송 부분실패 의심, reconciliation 대기. */
    public static final String STATUS_PENDING_RECONCILIATION = "PENDING_RECONCILIATION";

    /** 보상 stuck terminal (R5a ③) — COMPENSATING 이 SLA 내 종료되지 못함, 운영 개입 대상. */
    public static final String STATUS_COMPENSATION_STUCK = "COMPENSATION_STUCK";

    private A1OrderSaga() {
    }
}
