package io.github.orange2652.partner.channel.saga.ordr.application;

import io.github.orange2652.partner.channel.saga.ordr.domain.SagaState;
import io.github.orange2652.partner.channel.saga.ordr.domain.SagaStateRepository;
import io.micrometer.core.instrument.MeterRegistry;
import java.time.LocalDateTime;
import java.util.List;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * A1 saga stuck 스캐너 (R5a ③) — reply 미수신/크래시로 SLA 내 진행하지 못한 saga 를 주기적으로 찾아 복구 액션을
 * 취한다. outbox 재발행(이벤트 재전달)이 못 푸는 "saga 가 멈췄다"는 사실을 **오케스트레이터가 자기 상태
 * ({@code saga_state.last_transition_at})로 직접 인지**하고 책임지는 층이다.
 *
 * <p><b>SLA</b>({@link SagaTimeoutProperties}): RUNNING {@code runningSla}(기본 30분) / COMPENSATING
 * {@code compensatingSla}(기본 1시간) 초과 시 stuck. scanner 는 saga 자기 schema 만 조회하고, 보상 트리거는 직접
 * 호출이 아닌 {@link OrderSagaCompensator}(내부, event 발행)로 위임한다({@code ApplicationModules.verify()} 정합).</p>
 *
 * <p><b>step 분기(R2 4-step 매핑, R5a ③ 표)</b>:</p>
 * <ul>
 *   <li>RUNNING step1/step2(UNCONFIRMED_ORDER/VALIDATE): 외부 WRITE 이전이라 안전하게 보상 트리거 → COMPENSATING.</li>
 *   <li>RUNNING step3(CHANNEL_CONFIRM): 외부 전이 여부를 모르면 blind 액션이 위험하다. <b>외부 GET 가드
 *       (queryStatus)로 advance/보상 판정이 정석이나, 그 query-event 쌍은 R6 결정으로 Phase 4 이연</b>이라 본 v1 은
 *       자동 조치를 보류하고 식별 로그+메트릭만 남긴다(blind 보상 금지).</li>
 *   <li>RUNNING step4(CONFIRMED_ORDER, Pivot): 부분실패 의심 → {@code PENDING_RECONCILIATION}(reconciliation 워커는
 *       Phase 4).</li>
 *   <li>COMPENSATING: SLA 초과 → {@code COMPENSATION_STUCK} terminal(운영 개입).</li>
 * </ul>
 *
 * <p><b>이연(설계는 됨)</b>: ① 외부 GET reconciliation(step3, query-event 쌍 Phase 4) ② 다중 인스턴스 ShedLock
 * (현재 단일 인스턴스 가정) ③ PENDING_RECONCILIATION→CONFIRMED_ORDER_FAILED 2차 타임아웃.</p>
 */
@Slf4j
@Component
@EnableConfigurationProperties(SagaTimeoutProperties.class)
class SagaTimeoutScanner {

    /** stuck 감지 메트릭(R5a ⑤ — 공용 추상 자제, 상수 문자열). tag: step / action. */
    private static final String METRIC = "saga.timeout.detected";

    private final SagaStateRepository sagaStateRepository;
    private final SagaTimeoutProperties properties;
    private final OrderSagaCompensator compensator;
    private final MeterRegistry meterRegistry;

    SagaTimeoutScanner(SagaStateRepository sagaStateRepository, SagaTimeoutProperties properties,
            OrderSagaCompensator compensator, MeterRegistry meterRegistry) {
        this.sagaStateRepository = sagaStateRepository;
        this.properties = properties;
        this.compensator = compensator;
        this.meterRegistry = meterRegistry;
    }

    @Scheduled(fixedDelayString = "${partner-channel.saga.timeout.scan-interval-ms:60000}")
    void scan() {
        LocalDateTime now = LocalDateTime.now();

        List<SagaState> runningStuck = sagaStateRepository.findStuck(
                A1OrderSaga.STATUS_RUNNING, now.minus(properties.runningSla()));
        for (SagaState state : runningStuck) {
            handleRunningStuck(state);
        }

        List<SagaState> compensatingStuck = sagaStateRepository.findStuck(
                A1OrderSaga.STATUS_COMPENSATING, now.minus(properties.compensatingSla()));
        for (SagaState state : compensatingStuck) {
            log.info("보상 stuck → COMPENSATION_STUCK(운영 개입): sagaId={} correlationKey={} step={}",
                    state.sagaId(), state.correlationKey(), state.currentStep());
            count(state.currentStep(), "compensation_stuck");
            sagaStateRepository.save(state.withStatus(A1OrderSaga.STATUS_COMPENSATION_STUCK));
        }
    }

    private void handleRunningStuck(SagaState state) {
        switch (state.currentStep()) {
            case A1OrderSaga.STEP_UNCONFIRMED_ORDER, A1OrderSaga.STEP_VALIDATE -> {
                log.info("RUNNING stuck(외부 WRITE 이전) → 보상 트리거: sagaId={} correlationKey={} step={}",
                        state.sagaId(), state.correlationKey(), state.currentStep());
                count(state.currentStep(), "compensate");
                compensator.start(state.sagaId(), "TIMEOUT:" + state.currentStep());
            }
            case A1OrderSaga.STEP_CHANNEL_CONFIRM -> {
                log.info("CHANNEL_CONFIRM stuck → 외부 GET reconciliation 필요(Phase4 query-event 이연), 자동조치 보류: "
                        + "sagaId={} correlationKey={}", state.sagaId(), state.correlationKey());
                count(state.currentStep(), "deferred_external_check");
            }
            case A1OrderSaga.STEP_CONFIRMED_ORDER -> {
                log.info("Pivot(CONFIRMED_ORDER) stuck → PENDING_RECONCILIATION: sagaId={} correlationKey={}",
                        state.sagaId(), state.correlationKey());
                count(state.currentStep(), "pending_reconciliation");
                sagaStateRepository.save(state.withStatus(A1OrderSaga.STATUS_PENDING_RECONCILIATION));
            }
            default -> log.info("알 수 없는 step 의 RUNNING stuck(조치 없음): sagaId={} step={}",
                    state.sagaId(), state.currentStep());
        }
    }

    private void count(String step, String action) {
        meterRegistry.counter(METRIC, "step", step, "action", action).increment();
    }
}
