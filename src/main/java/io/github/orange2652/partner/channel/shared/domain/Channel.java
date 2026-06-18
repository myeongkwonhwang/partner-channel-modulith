package io.github.orange2652.partner.channel.shared.domain;

import java.util.Optional;

/**
 * 외부 채널 식별자. 본 프로젝트가 지원하는 멀티채널 enum.
 *
 * <p>R5b 다채널 결정으로 {@code TOSS / NAVER / COUPANG} 3채널 정의. 유튜브쇼핑은 네이티브 주문 API
 * 부재로 보류 (연동 커머스 플랫폼 확정 시 편입).</p>
 *
 * <p><b>직렬화 정책</b> (MSA 승계): 외부/이벤트 payload 와 DB 컬럼은 enum 이름 ({@link #name()}) 을
 * code 로 사용한다. event record 필드는 점진 전환을 위해 {@code String channel} 을 유지하며, 비교·검증
 * 시에만 본 enum 으로 변환한다 ({@link #fromCode}). 멱등 키 {@code "{channel}:{externalOrderProductId}"}
 * 의 channel 부분도 {@link #code()} 를 쓴다.</p>
 */
public enum Channel {

    TOSS,
    NAVER,
    COUPANG;

    /**
     * @return 일치하는 채널. 미지원/{@code null} 이면 {@link Optional#empty()} — 호출자가 도메인
     *         예외 (예: {@code UnsupportedChannelException}) 로 wrap 한다.
     */
    public static Optional<Channel> fromCode(String code) {
        if (code == null) {
            return Optional.empty();
        }
        for (Channel c : values()) {
            if (c.name().equals(code)) {
                return Optional.of(c);
            }
        }
        return Optional.empty();
    }

    public String code() {
        return name();
    }
}
