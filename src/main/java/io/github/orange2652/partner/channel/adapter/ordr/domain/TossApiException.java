package io.github.orange2652.partner.channel.adapter.ordr.domain;

/**
 * 토스 API 호출 실패 (R6) — HTTP 오류 또는 응답 {@code resultType=FAIL}({@code errorCode}). {@link ChannelException}
 * 의 final 구체.
 *
 * <p>{@code transient_} 플래그로 재시도 가능 여부를 구분한다 — 토스 {@code TOO_MANY_REQUEST}(HTTP 200 으로 옴 — 함정)
 * 나 5xx 는 transient(재시도 대상), {@code INVALID_REQUEST} 등은 비-transient. confirm/보상 핸들러는 본 예외를
 * RuntimeException 으로 받아 reply.failed(재시도/보상)로 라우팅한다.</p>
 */
public final class TossApiException extends ChannelException {

    private static final String CODE = "ADAPTER_TOSS_API_ERROR";

    private final boolean transient_;

    public TossApiException(String message, boolean isTransient) {
        super(CODE, message);
        this.transient_ = isTransient;
    }

    public boolean isTransient() {
        return transient_;
    }
}
