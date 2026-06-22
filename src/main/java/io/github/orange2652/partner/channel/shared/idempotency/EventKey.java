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

    /**
     * 이미 검증된 채널 코드 문자열로 키를 만든다 — 예: {@code OrderReceivedEvent.channel}(자사 polling 이 발행해
     * 코드 유효성이 이미 보장됨). 포맷을 {@link #of(Channel, String)} 와 한 곳에 모으기 위한 오버로드.
     *
     * @param channelCode {@link Channel#code()} 형식의 채널 코드
     */
    public static String of(String channelCode, String externalOrderProductId) {
        Objects.requireNonNull(channelCode, "channelCode");
        Objects.requireNonNull(externalOrderProductId, "externalOrderProductId");
        return channelCode + SEPARATOR + externalOrderProductId;
    }

    /**
     * EventKey/correlationKey 에서 채널 코드(첫 {@code SEPARATOR} 앞부분)를 꺼낸다. 포맷을 본 클래스가 소유하므로
     * 파싱도 여기 둔다. 채널 코드({@code TOSS/NAVER/COUPANG})에는 {@code SEPARATOR} 가 없어 첫 구분자 기준이 안전하다.
     *
     * @throws IllegalArgumentException 구분자가 없는 잘못된 형식
     */
    public static String channelCodeOf(String key) {
        Objects.requireNonNull(key, "key");
        int i = key.indexOf(SEPARATOR);
        if (i < 0) {
            throw new IllegalArgumentException("잘못된 EventKey 형식(구분자 없음): " + key);
        }
        return key.substring(0, i);
    }

    /**
     * EventKey/correlationKey 에서 외부 식별자(첫 {@code SEPARATOR} 뒷부분)를 꺼낸다. {@link #channelCodeOf} 의 대칭
     * 메서드 — 채널 코드에는 {@code SEPARATOR} 가 없어 첫 구분자 기준 분리가 안전하다(외부 식별자에 구분자가 있어도 보존).
     *
     * @throws IllegalArgumentException 구분자가 없는 잘못된 형식
     */
    public static String externalOrderProductIdOf(String key) {
        Objects.requireNonNull(key, "key");
        int i = key.indexOf(SEPARATOR);
        if (i < 0) {
            throw new IllegalArgumentException("잘못된 EventKey 형식(구분자 없음): " + key);
        }
        return key.substring(i + SEPARATOR.length());
    }

    private EventKey() {
    }
}
