package io.github.orange2652.partner.channel;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.github.orange2652.partner.channel.adapter.ordr.application.CompensationPort;
import io.github.orange2652.partner.channel.adapter.ordr.application.ConfirmStrategy;
import io.github.orange2652.partner.channel.core.ordr.application.Validator;
import io.github.orange2652.partner.channel.saga.ordr.application.A1OrderSaga;
import io.github.orange2652.partner.channel.saga.ordr.domain.SagaStateRepository;
import io.github.orange2652.partner.channel.shared.domain.Channel;
import io.github.orange2652.partner.channel.shared.event.ordr.OrderReceivedEvent;
import io.github.orange2652.partner.channel.shared.idempotency.EventKey;
import java.time.Duration;
import java.time.Instant;
import java.util.concurrent.atomic.AtomicLong;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.annotation.Bean;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

/**
 * A1 SAGA 통합 테스트 — 단위(Port mock)가 못 본 <b>실제 비동기·Tx 경계·내장 outbox dispatch</b>를 풀 컨텍스트 ({@code @SpringBootTest})에서 처음
 * 검증한다. 핵심 가치는 step3 channelConfirm 의 평범한 {@code @TransactionalEventListener(AFTER_COMMIT)}(=
 * {@code @ApplicationModuleListener} 아님)가 in-VM 이벤트 체인 에서 실제로 전달되는지(→ {@code CONFIRMED_ORDER} 도달) 확인하는 것이다.
 *
 * <p><b>채널 스텁 전략(레지스트리 EnumMap NPE 회피)</b>: 실제 토스 {@code ConfirmStrategy}/{@code CompensationPort}
 * 빈(채널 {@code TOSS})은 컨텍스트에 그대로 둔 채(휴면), 본 테스트는 <b>{@code NAVER} 채널 fake 빈</b>을 {@code @TestConfiguration} 으로 추가하고 이벤트도
 * {@code channel=NAVER} 로 발행한다. 이렇게 하면 (1) fake 의 {@code channel()} 이 빈 생성 시점에 이미 {@code NAVER} 라 레지스트리 {@code EnumMap}
 * 구성이 안전하고(`@MockitoBean` 으로 Port 를 교체하면 mock 의 {@code channel()} 이 refresh 시점에 null → NPE), (2) 실제 토스(TOSS) 빈과 키가 달라
 * 충돌하지 않으며, (3) 라우팅이 NAVER 라 실제 외부 HTTP 가 호출되지 않는다. saga 는 Phase 2 에서 채널 불문이라 (Validator 는 채널 지원 여부만 판정) 비동기·Tx·outbox
 * 검증 목적에 부합한다.
 *
 * <p><b>외부화 off</b>: {@code OrderReceivedEvent} 가 {@code @Externalized} 라 풀 컨텍스트에서 Kafka 발행을 시도한다.
 * {@code spring.modulith.events.externalization.enabled=false} 로 꺼 Kafka 컨테이너 없이 내부 체인에 집중한다.
 *
 * <p><b>실행 전제: Docker 필요</b>(Testcontainers 4 schema + 내장 outbox {@code event_publication}). {@code @Async}
 * 미구성({@code @EnableAsync} 부재)이라 체인은 동기로 풀릴 수 있으나, 동기/비동기 결과를 모두 흡수하도록 Awaitility 로 saga_state 도달을 폴링한다.
 */
@SpringBootTest(properties = {
    "spring.modulith.events.externalization.enabled=false"
    // ddl-auto 는 프로덕션과 동일하게 application.yml 의 validate 를 쓴다 — event_publication 은 Flyway
    // (db/migration/events, events_schema)가 생성하므로 더는 update 우회가 필요 없다(부팅 productionize 완결).
})
@Testcontainers
@Tag("testcontainers")
class A1OrderSagaIntegrationTest {

    @Container
    @ServiceConnection
    static final PostgreSQLContainer POSTGRES = new PostgreSQLContainer("postgres:16-alpine");

    private static final AtomicLong SEQ = new AtomicLong(1_000_000L);
    private static final Duration TIMEOUT = Duration.ofSeconds(20);

    @Autowired
    private ApplicationEventPublisher publisher;
    @Autowired
    private PlatformTransactionManager txManager;
    @Autowired
    private SagaStateRepository sagaStateRepository;

    @Autowired
    @Qualifier("naverConfirmStrategy")
    private ConfirmStrategy naverConfirm;
    @Autowired
    @Qualifier("naverCompensationPort")
    private CompensationPort naverCompensate;

    /**
     * core Validator 는 단일 public 빈(레지스트리 무관) — REJECTED 유도용으로 교체. 기본은 PASSED 로 스텁한다.
     */
    @MockitoBean
    private Validator validator;

    private TransactionTemplate tx;

    private static String uniqueId() {
        return "NAVERORD-" + SEQ.incrementAndGet();
    }

    @BeforeEach
    void setUp() {
        tx = new TransactionTemplate(txManager);
        // fake 빈은 @MockitoBean 이 아니라 자동 리셋되지 않는다 — 메서드 간 행위 누수 방지로 매번 초기화.
        org.mockito.Mockito.reset(naverConfirm, naverCompensate);
        when(naverConfirm.channel()).thenReturn(Channel.NAVER);
        when(naverCompensate.channel()).thenReturn(Channel.NAVER);
        // 기본 검증 통과(해피/외부실패 경로 공용). 거절 경로 테스트만 재스텁한다.
        when(validator.validate(any(), any())).thenReturn(Validator.Verdict.passed());
    }

    @Test
    void 해피패스_step3_AFTER_COMMIT_전달되어_CONFIRMED_ORDER_도달() {
        String externalOrderProductId = uniqueId();
        String correlationKey = EventKey.of(Channel.NAVER, externalOrderProductId);

        publishOrderReceivedInTx(externalOrderProductId);

        // 빈 Optional 도 AssertionError 로 떨어져 Awaitility 가 재시도하도록 AssertJ Optional 단언을 쓴다
        // (orElseThrow() 의 NoSuchElementException 은 Awaitility 가 무시하지 않아 행 커밋 전이면 즉시 실패).
        await().atMost(TIMEOUT).untilAsserted(() ->
            assertThat(sagaStateRepository.findByCorrelationKey(correlationKey)).hasValueSatisfying(state -> {
                assertThat(state.currentStep()).isEqualTo(A1OrderSaga.STEP_CONFIRMED_ORDER);
                assertThat(state.status()).isEqualTo("RUNNING"); // step4 미배선 — terminal 아님
            }));
        // step3 외부 통보가 실제로 호출됐다(AFTER_COMMIT 리스너 전달 확인).
        verify(naverConfirm).confirm(externalOrderProductId);
    }

    @Test
    void 보상패스_step3_외부통보_실패시_COMPENSATED() {
        String externalOrderProductId = uniqueId();
        String correlationKey = EventKey.of(Channel.NAVER, externalOrderProductId);
        // step3 confirm 이 던지면 → ChannelConfirmReply.failed → R4 보상(외부 cancel → staging 취소) → COMPENSATED.
        doThrow(new IllegalStateException("외부 통보 실패(테스트)"))
            .when(naverConfirm).confirm(externalOrderProductId);

        publishOrderReceivedInTx(externalOrderProductId);

        await().atMost(TIMEOUT).untilAsserted(() ->
            assertThat(sagaStateRepository.findByCorrelationKey(correlationKey))
                .hasValueSatisfying(state ->
                    assertThat(state.status()).isEqualTo(A1OrderSaga.STATUS_COMPENSATED)));
        verify(naverCompensate).cancel(eq(externalOrderProductId), any());
    }

    @Test
    void 보상패스_validate_REJECTED시_step3_없이_COMPENSATED() {
        String externalOrderProductId = uniqueId();
        String correlationKey = EventKey.of(Channel.NAVER, externalOrderProductId);
        // validate 거절 → ValidateReplyHandler 가 곧장 보상 시작(step3 confirm 미실행) → seller-cancel + staging.
        when(validator.validate(any(), any()))
            .thenReturn(Validator.Verdict.rejected("STOCK_OUT", "재고 없음(테스트)"));

        publishOrderReceivedInTx(externalOrderProductId);

        await().atMost(TIMEOUT).untilAsserted(() ->
            assertThat(sagaStateRepository.findByCorrelationKey(correlationKey))
                .hasValueSatisfying(state ->
                    assertThat(state.status()).isEqualTo(A1OrderSaga.STATUS_COMPENSATED)));
        verify(naverConfirm, never()).confirm(any()); // 거절은 step3 진입 전 분기
        verify(naverCompensate).cancel(eq(externalOrderProductId), any());
    }

    /**
     * {@code @ApplicationModuleListener}(= {@code @TransactionalEventListener AFTER_COMMIT})가 발동하려면 발행이 활성 Tx 안이어야
     * 한다(내장 outbox 적재 후 커밋 시 dispatch). 자사 polling 발행을 모사해 Tx 경계 안에서 발행한다.
     */
    private void publishOrderReceivedInTx(String externalOrderProductId) {
        Instant now = Instant.now();
        OrderReceivedEvent event = new OrderReceivedEvent(
            Channel.NAVER.code(),
            externalOrderProductId,
            now.minus(Duration.ofMinutes(40)), // 30분 경과(R1) — 본 테스트는 in-VM 직접 발행이라 가드 무관
            now,
            "{\"orderProductStatus\":\"PAID\"}");
        tx.executeWithoutResult(status -> publisher.publishEvent(event));
    }

    @TestConfiguration
    static class ChannelStubConfig {

        /**
         * NAVER fake — {@code channel()} 을 빈 생성 시점에 스텁해 레지스트리 EnumMap 구성을 안전하게 한다.
         */
        @Bean
        ConfirmStrategy naverConfirmStrategy() {
            ConfirmStrategy strategy = mock(ConfirmStrategy.class);
            when(strategy.channel()).thenReturn(Channel.NAVER);
            return strategy;
        }

        @Bean
        CompensationPort naverCompensationPort() {
            CompensationPort port = mock(CompensationPort.class);
            when(port.channel()).thenReturn(Channel.NAVER);
            return port;
        }
    }
}
