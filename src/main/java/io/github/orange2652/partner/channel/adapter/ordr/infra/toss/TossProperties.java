package io.github.orange2652.partner.channel.adapter.ordr.infra.toss;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 토스쇼핑 API 연동 설정 (R6) — {@code partner-channel.toss.*}. 비밀값은 환경변수/시크릿으로 주입한다(yml 평문 금지).
 *
 * @param baseUrl      ShoppingFep API 베이스 URL (예: {@code https://.../api/v3/shopping-fep})
 * @param tokenUrl     OAuth2 client credentials 토큰 발급 URL (full)
 * @param clientId     OAuth2 client id
 * @param clientSecret OAuth2 client secret
 * @param queryLookbackDays 사전 GET 가드 단건 조회 시 거슬러 볼 기간(일) — 토스 단건 GET 부재로 {@code /orders/v2}
 *                          기간 조회를 쓴다(최대 31). 기본 31.
 */
@ConfigurationProperties(prefix = "partner-channel.toss")
public record TossProperties(
        String baseUrl,
        String tokenUrl,
        String clientId,
        String clientSecret,
        Integer queryLookbackDays
) {
    private static final int DEFAULT_LOOKBACK_DAYS = 31;

    public int lookbackDaysOrDefault() {
        return queryLookbackDays == null ? DEFAULT_LOOKBACK_DAYS : queryLookbackDays;
    }
}
