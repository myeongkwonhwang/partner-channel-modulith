package io.github.orange2652.partner.channel.saga.ordr.domain;

import io.github.orange2652.partner.channel.shared.exception.PartnerChannelException;

/**
 * saga 도메인 예외 베이스 (abstract) — {@link PartnerChannelException} 의 모듈 sub. 말단은 {@code final} 구체.
 *
 * <p>복구 불가한 saga 불변식 위반(예: reply 인데 saga_state 부재)을 unchecked 로 표현한다(Effective Java Item 70).
 * 식별 정보(sagaId 등)는 구체 예외 메시지 본문에 명시(프로젝트 규칙).</p>
 */
public abstract class SagaException extends PartnerChannelException {

    protected SagaException(String code, String message) {
        super(code, message);
    }

    protected SagaException(String code, String message, Throwable cause) {
        super(code, message, cause);
    }
}
