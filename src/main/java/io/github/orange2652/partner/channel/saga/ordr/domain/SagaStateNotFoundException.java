package io.github.orange2652.partner.channel.saga.ordr.domain;

import java.util.UUID;

/**
 * reply 를 받았는데 해당 {@code sagaId} 의 saga_state 가 없을 때 — 불변식 위반(reply 는 saga 시작·command 발행
 * 이후에만 존재). 복구 불가 unchecked.
 */
public final class SagaStateNotFoundException extends SagaException {

    public SagaStateNotFoundException(UUID sagaId) {
        super("SAGA_STATE_NOT_FOUND", "saga 상태를 찾을 수 없습니다: sagaId=" + sagaId);
    }
}
