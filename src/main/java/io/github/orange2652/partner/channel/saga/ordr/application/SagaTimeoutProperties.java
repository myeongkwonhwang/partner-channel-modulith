package io.github.orange2652.partner.channel.saga.ordr.application;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * {@link SagaTimeoutScanner} SLA 설정 (R5a ③/④) — 하드코딩 상수 대신 외부화해 운영/채널별 튜닝을 허용한다.
 *
 * <p>기본값은 MSA 승계 SLA (RUNNING 30분 / COMPENSATING 1시간). dev/테스트는 짧은 값으로 override 해 stuck→복구를
 * 빠르게 관측한다(예: {@code --partner-channel.saga.timeout.running-sla=15s}).</p>
 *
 * @param runningSla      RUNNING 상태가 이 기간을 넘겨 멈춰 있으면 stuck 으로 본다
 * @param compensatingSla COMPENSATING 상태가 이 기간을 넘기면 보상 stuck 으로 본다
 */
@ConfigurationProperties("partner-channel.saga.timeout")
public record SagaTimeoutProperties(Duration runningSla, Duration compensatingSla) {

    public SagaTimeoutProperties {
        if (runningSla == null) {
            runningSla = Duration.ofMinutes(30);
        }
        if (compensatingSla == null) {
            compensatingSla = Duration.ofHours(1);
        }
    }
}
