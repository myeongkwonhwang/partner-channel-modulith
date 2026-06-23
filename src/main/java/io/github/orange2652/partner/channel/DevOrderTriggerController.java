package io.github.orange2652.partner.channel;

import io.github.orange2652.partner.channel.shared.event.ordr.OrderReceivedEvent;
import io.github.orange2652.partner.channel.shared.idempotency.EventKey;
import java.time.Duration;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.annotation.Profile;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * dev 전용 A1 흐름 트리거 — 자사 polling(PollingStrategy 구현 Phase2 이연)이 없어 실행 중인 앱에는 {@link OrderReceivedEvent}
 * 발행원이 없다. 본 컨트롤러가 그 발행을 모사해 A1 saga 체인(sagaStart→step1→step2→step3→CONFIRMED_ORDER / 보상)을
 * 로컬에서 눈으로 볼 수 있게 한다.
 *
 * <p><b>dev 프로파일에서만 활성</b>({@code @Profile("dev")}). 애플리케이션 루트 패키지에 둔다(모듈이 아닌 app shell) —
 * 참조는 open module {@code shared}({@code OrderReceivedEvent}/{@code EventKey})뿐이라 모듈 경계(verify)에 무해하다.
 * {@code @ApplicationModuleListener}(=AFTER_COMMIT) 가 발동하려면 활성 Tx 안에서 발행해야 하므로 {@link TransactionTemplate}
 * 로 감싼다.</p>
 *
 * <p>관측: {@code POST /dev/orders} → 응답의 {@code correlationKey} 로 {@code saga_schema.saga_state} 전이와 앱 로그를
 * 추적한다. {@code fail=true} 면 dev ConfirmStrategy 가 step3 에서 예외 → R4 보상(COMPENSATED) 경로를 탄다.</p>
 */
@Slf4j
@RestController
@Profile("dev")
@RequestMapping("/dev/orders")
class DevOrderTriggerController {

    /** R1 30분 가드를 자연 통과시키기 위한 과거 시각 오프셋(본 트리거는 in-VM 직접 발행이라 가드와 무관하지만 의미를 맞춘다). */
    private static final Duration ORDERED_AGO = Duration.ofMinutes(40);

    private final ApplicationEventPublisher publisher;
    private final TransactionTemplate tx;

    DevOrderTriggerController(ApplicationEventPublisher publisher, PlatformTransactionManager txManager) {
        this.publisher = publisher;
        this.tx = new TransactionTemplate(txManager);
    }

    /**
     * A1 흐름을 1건 시작한다.
     *
     * @param channel                채널 코드(기본 {@code NAVER} — dev 채널 스텁이 등록된 채널)
     * @param externalOrderProductId 외부 주문상품 식별자(미지정 시 자동 생성). "fail" 포함 시 step3 강제 실패→보상 관측
     * @param fail                   true 면 식별자에 "fail" 을 넣어 보상 경로로 보낸다
     */
    @PostMapping
    Map<String, String> trigger(
            @RequestParam(defaultValue = "NAVER") String channel,
            @RequestParam(required = false) String externalOrderProductId,
            @RequestParam(defaultValue = "false") boolean fail) {

        String extId = externalOrderProductId != null ? externalOrderProductId
                : (fail ? "DEV-fail-" : "DEV-") + System.currentTimeMillis();
        Instant now = Instant.now();
        OrderReceivedEvent event = new OrderReceivedEvent(
                channel, extId, now.minus(ORDERED_AGO), now, "{\"orderProductStatus\":\"PAID\",\"dev\":true}");

        tx.executeWithoutResult(status -> publisher.publishEvent(event));

        String correlationKey = EventKey.of(channel, extId);
        log.info("[DEV] OrderReceivedEvent 발행 → A1 흐름 시작: correlationKey={} fail={}", correlationKey, fail);

        Map<String, String> body = new LinkedHashMap<>();
        body.put("correlationKey", correlationKey);
        body.put("channel", channel);
        body.put("externalOrderProductId", extId);
        body.put("fail", String.valueOf(fail));
        body.put("expect", fail ? "COMPENSATING → COMPENSATED" : "→ CONFIRMED_ORDER (step4 미배선, RUNNING 유지)");
        body.put("watch", "SELECT current_step,status FROM saga_schema.saga_state WHERE correlation_key='"
                + correlationKey + "';");
        return body;
    }
}
