package io.github.orange2652.partner.channel.shared.exception;

/**
 * 본 프로젝트 도메인 예외 최상위 베이스.
 *
 * <p>모든 도메인 예외는 {@code abstract} 모듈/도메인 sub (예: {@code SagaException},
 * {@code ChannelException}, {@code LogisticsException}) 를 거쳐 본 클래스를 상속하고, 말단은
 * {@code final} 구체 예외다. {@code abstract} + 패키지 위치 컨벤션으로 직접 인스턴스화를 막는다.</p>
 *
 * <p><b>식별 정보 정책</b> (프로젝트 규칙): 메시지 본문에 원인 식별 정보 (sagaId / orderId /
 * externalOrderProductId 등) 를 반드시 포함한다. {@link #code} 는 운영/모니터링용 분류 코드.</p>
 */
public abstract class PartnerChannelException extends RuntimeException {

    private final String code;

    protected PartnerChannelException(String code, String message) {
        super(message);
        this.code = code;
    }

    protected PartnerChannelException(String code, String message, Throwable cause) {
        super(message, cause);
        this.code = code;
    }

    public String code() {
        return code;
    }
}
