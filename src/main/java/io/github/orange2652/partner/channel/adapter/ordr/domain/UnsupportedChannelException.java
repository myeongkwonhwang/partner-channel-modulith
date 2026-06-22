package io.github.orange2652.partner.channel.adapter.ordr.domain;

import java.util.UUID;

/**
 * 지원하지 않는 채널 코드 — {@code Channel.fromCode} miss 를 도메인 예외로 wrap (Channel javadoc 의 지정 wrap 지점).
 *
 * <p>채널 코드는 polling/검증 단계에서 이미 {@code Channel} enum 으로 확정되므로 실제로는 발생하지 않아야 하나,
 * 방어적 접근(Item 49)으로 잘못된 코드가 step3 까지 흘러온 경우를 unchecked 로 막는다.</p>
 */
public final class UnsupportedChannelException extends ChannelException {

    private static final String CODE = "ADAPTER_UNSUPPORTED_CHANNEL";

    public UnsupportedChannelException(String channelCode, UUID sagaId) {
        super(CODE, "지원하지 않는 채널 코드: channelCode=" + channelCode + " sagaId=" + sagaId);
    }
}
