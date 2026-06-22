package io.github.orange2652.partner.channel.saga.ordr.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.UUID;
import org.junit.jupiter.api.Test;

/**
 * {@link SagaState} 도메인 순수 단위 테스트 (프레임워크 0).
 *
 * <p>검증 불변식: ① 필수 필드 {@code requireNonNull} 가드, ② {@code start} 정적 팩토리(id=null·status=RUNNING·
 * attempts=0·시각 채움), ③ {@code advanceTo}/{@code withStatus}/{@code incrementReconciliationAttempts} 불변 전이
 * (새 인스턴스·해당 필드만 변경·원본 보존). 시각은 {@code now()} 직접 단언 대신 변경 여부만 본다(결정적).</p>
 */
class SagaStateTest {

    private static final UUID SAGA_ID = UUID.fromString("00000000-0000-0000-0000-000000000001");
    private static final String TYPE = "A1_ORDER";
    private static final String CORRELATION = "TOSS:o-1";
    private static final String STEP = "UNCONFIRMED_ORDER";
    private static final String PAYLOAD = "{\"channel\":\"TOSS\"}";

    @Test
    void 생성자는_sagaId_가_null_이면_NPE() {
        assertThatThrownBy(() -> SagaState.start(null, TYPE, CORRELATION, STEP, PAYLOAD))
                .isInstanceOf(NullPointerException.class)
                .hasMessage("sagaId");
    }

    @Test
    void 생성자는_sagaType_이_null_이면_NPE() {
        assertThatThrownBy(() -> SagaState.start(SAGA_ID, null, CORRELATION, STEP, PAYLOAD))
                .isInstanceOf(NullPointerException.class)
                .hasMessage("sagaType");
    }

    @Test
    void 생성자는_correlationKey_가_null_이면_NPE() {
        assertThatThrownBy(() -> SagaState.start(SAGA_ID, TYPE, null, STEP, PAYLOAD))
                .isInstanceOf(NullPointerException.class)
                .hasMessage("correlationKey");
    }

    @Test
    void 생성자는_currentStep_이_null_이면_NPE() {
        assertThatThrownBy(() -> SagaState.start(SAGA_ID, TYPE, CORRELATION, null, PAYLOAD))
                .isInstanceOf(NullPointerException.class)
                .hasMessage("currentStep");
    }

    @Test
    void start_는_id_null_status_RUNNING_attempts_0_이고_시각을_채운다() {
        // when
        SagaState saga = SagaState.start(SAGA_ID, TYPE, CORRELATION, STEP, PAYLOAD);

        // then
        assertThat(saga.id()).isNull();
        assertThat(saga.sagaId()).isEqualTo(SAGA_ID);
        assertThat(saga.sagaType()).isEqualTo(TYPE);
        assertThat(saga.correlationKey()).isEqualTo(CORRELATION);
        assertThat(saga.currentStep()).isEqualTo(STEP);
        assertThat(saga.status()).isEqualTo("RUNNING");
        assertThat(saga.payload()).isEqualTo(PAYLOAD);
        assertThat(saga.reconciliationAttempts()).isZero();
        assertThat(saga.startedAt()).isNotNull();
        assertThat(saga.lastTransitionAt()).isNotNull();
    }

    @Test
    void start_는_payload_null_을_허용한다() {
        SagaState saga = SagaState.start(SAGA_ID, TYPE, CORRELATION, STEP, null);

        assertThat(saga.payload()).isNull();
    }

    @Test
    void advanceTo_는_currentStep_만_전진하고_원본은_불변() {
        // given
        SagaState original = SagaState.start(SAGA_ID, TYPE, CORRELATION, STEP, PAYLOAD);

        // when
        SagaState advanced = original.advanceTo("VALIDATE");

        // then — 새 인스턴스, currentStep 만 변경, 나머지 보존
        assertThat(advanced).isNotSameAs(original);
        assertThat(advanced.currentStep()).isEqualTo("VALIDATE");
        assertThat(advanced.sagaId()).isEqualTo(SAGA_ID);
        assertThat(advanced.status()).isEqualTo("RUNNING");
        assertThat(advanced.reconciliationAttempts()).isZero();
        // 원본 불변
        assertThat(original.currentStep()).isEqualTo(STEP);
    }

    @Test
    void advanceTo_는_nextStep_이_null_이면_NPE() {
        SagaState original = SagaState.start(SAGA_ID, TYPE, CORRELATION, STEP, PAYLOAD);

        assertThatThrownBy(() -> original.advanceTo(null))
                .isInstanceOf(NullPointerException.class)
                .hasMessage("nextStep");
    }

    @Test
    void withStatus_는_status_만_전이하고_원본은_불변() {
        // given
        SagaState original = SagaState.start(SAGA_ID, TYPE, CORRELATION, STEP, PAYLOAD);

        // when
        SagaState compensating = original.withStatus("COMPENSATING");

        // then
        assertThat(compensating).isNotSameAs(original);
        assertThat(compensating.status()).isEqualTo("COMPENSATING");
        assertThat(compensating.currentStep()).isEqualTo(STEP);
        assertThat(original.status()).isEqualTo("RUNNING");
    }

    @Test
    void incrementReconciliationAttempts_는_횟수만_1_증가하고_원본은_불변() {
        // given
        SagaState original = SagaState.start(SAGA_ID, TYPE, CORRELATION, STEP, PAYLOAD);

        // when
        SagaState retried = original.incrementReconciliationAttempts().incrementReconciliationAttempts();

        // then
        assertThat(retried.reconciliationAttempts()).isEqualTo(2);
        assertThat(original.reconciliationAttempts()).isZero();
    }
}
