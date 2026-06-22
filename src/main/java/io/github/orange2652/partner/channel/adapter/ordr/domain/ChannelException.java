package io.github.orange2652.partner.channel.adapter.ordr.domain;

import io.github.orange2652.partner.channel.shared.exception.PartnerChannelException;

/**
 * adapter(외부 채널) 도메인 예외 베이스 (abstract) — {@link PartnerChannelException} 의 모듈 sub. 말단은
 * {@code final} 구체.
 *
 * <p>외부 채널 연동에서 복구 불가한 불변식 위반/설정 오류를 unchecked 로 표현한다(Effective Java Item 70).
 * 식별 정보(channel / sagaId / externalOrderProductId 등)는 구체 예외 메시지 본문에 명시(프로젝트 규칙).
 * step3 channelConfirm·이후 B1 dispatch·R4 보상이 본 계층을 공유한다.</p>
 */
public abstract class ChannelException extends PartnerChannelException {

    protected ChannelException(String code, String message) {
        super(code, message);
    }

    protected ChannelException(String code, String message, Throwable cause) {
        super(code, message, cause);
    }
}
