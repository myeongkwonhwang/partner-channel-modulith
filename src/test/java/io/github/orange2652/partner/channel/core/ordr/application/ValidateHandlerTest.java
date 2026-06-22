package io.github.orange2652.partner.channel.core.ordr.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;

import io.github.orange2652.partner.channel.shared.event.ordr.ValidateCommand;
import io.github.orange2652.partner.channel.shared.event.ordr.ValidateReply;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;

/**
 * {@link ValidateHandler} application 단위 테스트 — 실제 {@link Validator}(순수) + 발행만 mock.
 *
 * <p>검증: ① 지원 채널 → {@link ValidateReply#passed}, ② 미지원 채널 → {@link ValidateReply#rejected}
 * (사유 UNSUPPORTED_CHANNEL). raw 판매룰은 Validator 의 Phase 2 후속이라 본 테스트 범위 밖.</p>
 */
@ExtendWith(MockitoExtension.class)
class ValidateHandlerTest {

    private static final UUID SAGA_ID = UUID.fromString("00000000-0000-0000-0000-0000000000b2");
    private static final String RAW = "{\"orderProductId\":\"o-1\"}";

    @Mock
    private ApplicationEventPublisher events;

    private ValidateHandler handler() {
        return new ValidateHandler(new Validator(), events);
    }

    @Test
    void 지원_채널이면_PASSED_reply_를_발행한다() {
        // when
        handler().onValidate(new ValidateCommand(SAGA_ID, "TOSS", RAW));

        // then
        ArgumentCaptor<ValidateReply> captor = ArgumentCaptor.forClass(ValidateReply.class);
        verify(events).publishEvent(captor.capture());
        ValidateReply reply = captor.getValue();
        assertThat(reply.sagaId()).isEqualTo(SAGA_ID);
        assertThat(reply.result()).isEqualTo(ValidateReply.Result.PASSED);
    }

    @Test
    void 미지원_채널이면_REJECTED_reply_를_발행한다() {
        // when
        handler().onValidate(new ValidateCommand(SAGA_ID, "UNKNOWN", RAW));

        // then
        ArgumentCaptor<ValidateReply> captor = ArgumentCaptor.forClass(ValidateReply.class);
        verify(events).publishEvent(captor.capture());
        ValidateReply reply = captor.getValue();
        assertThat(reply.result()).isEqualTo(ValidateReply.Result.REJECTED);
        assertThat(reply.reasonCode()).isEqualTo("UNSUPPORTED_CHANNEL");
    }
}
