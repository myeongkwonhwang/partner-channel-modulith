package io.github.orange2652.partner.channel.shared.idempotency;

import io.github.orange2652.partner.channel.shared.domain.Channel;
import java.util.Objects;

/**
 * 멱등 이벤트 키 빌더 (R1/R3) — {@code "{channel}:{externalOrderProductId}"}.
 *
 * <p>매직 스트링 분산을 막기 위해 키 조합을 본 클래스 한 곳에 모은다. 형식 변경 시 본 메서드만 수정한다.
 * MSA 의 {@code "{partition}:{offset}"} 형식과 분기 — modulith 는 외부 채널 주문 단위 키를 쓴다.</p>
 */
public final class EventKey {

    private static final String SEPARATOR = ":";

    /** {@code "{channel.code()}:{externalOrderProductId}"}. */
    public static String of(Channel channel, String externalOrderProductId) {
        Objects.requireNonNull(channel, "channel");
        Objects.requireNonNull(externalOrderProductId, "externalOrderProductId");
        return channel.code() + SEPARATOR + externalOrderProductId;
    }

    private EventKey() {
    }
}
