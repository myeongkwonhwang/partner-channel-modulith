package io.github.orange2652.partner.channel.adapter.ordr.infra.dev;

import io.github.orange2652.partner.channel.adapter.ordr.application.CompensationPort;
import io.github.orange2652.partner.channel.shared.domain.CancelReason;
import io.github.orange2652.partner.channel.shared.domain.Channel;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

/**
 * dev 전용 {@link CompensationPort} — A1 보상 흐름(외부 seller-cancel)을 로컬에서 보기 위한 모사(항상 성공).
 * {@link DevChannelConfirmStrategy} 와 같은 {@code NAVER} 채널로 등록한다.
 *
 * <p><b>dev 프로파일에서만 활성</b>. 보상 경로 트리거(externalOrderProductId 에 "fail") 시 step3 실패 →
 * 외부 cancel 요청이 본 빈으로 와서 성공 로그를 남기고, saga 는 staging 취소까지 진행해 COMPENSATED 로 끝난다.</p>
 */
@Slf4j
@Component
@Profile("dev")
class DevChannelCompensationPort implements CompensationPort {

    @Override
    public Channel channel() {
        return Channel.NAVER;
    }

    @Override
    public void cancel(String externalOrderProductId, CancelReason reason) {
        log.info("[DEV] seller-cancel 성공 (외부 취소 모사): orderProductId={} charger={} reason={}",
                externalOrderProductId, reason.charger(), reason.reason());
    }
}
