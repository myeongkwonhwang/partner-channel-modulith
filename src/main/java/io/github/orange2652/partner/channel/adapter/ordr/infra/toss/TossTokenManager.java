package io.github.orange2652.partner.channel.adapter.ordr.infra.toss;

import io.github.orange2652.partner.channel.adapter.ordr.domain.TossApiException;
import java.time.Duration;
import java.time.Instant;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestClient;

/**
 * 토스 OAuth2 client credentials 토큰 관리 (R6) — 발급 토큰을 캐시하고 만료 임박/강제 무효화 시 재발급한다.
 *
 * <p>MSA 확정: 토큰 만료 ≈ 1년, refresh 없음(만료 시 재발급), 401 만 예외. 따라서 평상시 1회 발급 후 캐시 재사용,
 * 401(토큰 무효) 시 {@link #invalidate()} 후 재발급한다. 단순 동기화 캐시(저빈도 발급이라 충분).</p>
 *
 * <p>외부 호출이므로 호출자 Tx 밖에서 쓰인다. 실호출 검증은 sandbox 통합 단계 이연(단위는 미커버).</p>
 */
@Slf4j
@Component
class TossTokenManager {

    /** 만료 임박 안전 마진 — 표기 만료보다 이만큼 일찍 재발급. */
    private static final Duration EXPIRY_MARGIN = Duration.ofMinutes(5);

    private final RestClient tokenClient;
    private final TossProperties properties;

    private volatile String cachedToken;
    private volatile Instant expiresAt = Instant.EPOCH;

    TossTokenManager(TossProperties properties) {
        this.properties = properties;
        this.tokenClient = RestClient.builder().baseUrl(properties.tokenUrl()).build();
    }

    /** 유효 토큰을 반환(없거나 만료 임박이면 발급). */
    synchronized String bearerToken() {
        if (cachedToken == null || Instant.now().isAfter(expiresAt)) {
            issue();
        }
        return cachedToken;
    }

    /** 401 수신 시 캐시 무효화 — 다음 {@link #bearerToken()} 호출이 재발급한다. */
    synchronized void invalidate() {
        cachedToken = null;
        expiresAt = Instant.EPOCH;
    }

    private void issue() {
        MultiValueMap<String, String> form = new LinkedMultiValueMap<>();
        form.add("grant_type", "client_credentials");
        form.add("client_id", properties.clientId());
        form.add("client_secret", properties.clientSecret());

        TokenResponse response = tokenClient.post()
                .contentType(org.springframework.http.MediaType.APPLICATION_FORM_URLENCODED)
                .body(form)
                .retrieve()
                .body(TokenResponse.class);

        if (response == null || response.accessToken() == null) {
            throw new TossApiException("토스 토큰 발급 응답 비정상", true);
        }
        this.cachedToken = response.accessToken();
        long ttl = response.expiresIn() == null ? 3600L : response.expiresIn();
        this.expiresAt = Instant.now().plusSeconds(ttl).minus(EXPIRY_MARGIN);
        log.info("토스 토큰 발급 완료: ttl={}s", ttl);
    }

    /** OAuth2 토큰 응답(필요 필드만). */
    private record TokenResponse(
            @com.fasterxml.jackson.annotation.JsonProperty("access_token") String accessToken,
            @com.fasterxml.jackson.annotation.JsonProperty("expires_in") Long expiresIn
    ) {
    }
}
