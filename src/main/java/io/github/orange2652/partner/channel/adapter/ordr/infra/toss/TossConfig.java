package io.github.orange2652.partner.channel.adapter.ordr.infra.toss;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

/**
 * 토스 연동 설정 활성화 (R6) — {@link TossProperties} 바인딩. RestClient 는 {@link TossApiClient}/{@link TossTokenManager}
 * 가 properties 로부터 직접 구성한다(토큰 URL 과 API baseUrl 이 달라 별도).
 */
@Configuration
@EnableConfigurationProperties(TossProperties.class)
class TossConfig {
}
