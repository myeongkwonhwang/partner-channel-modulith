package io.github.orange2652.partner.channel.core.ordr.application;

import io.github.orange2652.partner.channel.shared.event.ordr.ValidateCommand;
import io.github.orange2652.partner.channel.shared.event.ordr.ValidateReply;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.modulith.events.ApplicationModuleListener;
import org.springframework.stereotype.Component;

/**
 * A1 step2 validate (R2) — saga 의 {@link ValidateCommand} 를 받아 판매가능 검증 후 {@link ValidateReply} 발행.
 *
 * <p><b>read-only</b>: DB 를 쓰지 않으므로 멱등 가드 생략(R3 — validate 제외). 중복 command 가 와도 같은 결과 reply 가
 * 다시 발행될 뿐이고, 수신측(saga validateReply)의 멱등 가드가 흡수한다. 외부 호출 없음.</p>
 *
 * <p>현재 {@link Validator} 는 채널 지원 여부까지만 판정한다(raw 판매룰은 Phase 2 후속 — Validator 의 TODO).</p>
 */
@Slf4j
@Component
@RequiredArgsConstructor
class ValidateHandler {

    private final Validator validator;
    private final ApplicationEventPublisher events;

    @ApplicationModuleListener
    void onValidate(ValidateCommand command) {
        Validator.Verdict verdict = validator.validate(command.channel(), command.raw());

        if (verdict.sellable()) {
            events.publishEvent(ValidateReply.passed(command.sagaId()));
            log.info("validate PASSED: sagaId={} channel={}", command.sagaId(), command.channel());
        } else {
            events.publishEvent(ValidateReply.rejected(command.sagaId(), verdict.reasonCode(), verdict.reasonMessage()));
            log.info("validate REJECTED: sagaId={} channel={} reason={}",
                    command.sagaId(), command.channel(), verdict.reasonCode());
        }
    }
}
