package io.github.orange2652.partner.channel.adapter.ordr.infra.dev;

import io.github.orange2652.partner.channel.adapter.ordr.application.ConfirmStrategy;
import io.github.orange2652.partner.channel.shared.domain.Channel;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

/**
 * dev 전용 {@link ConfirmStrategy} — A1 흐름을 로컬에서 눈으로 보기 위한 외부 통보 모사(실제 토스 HTTP 없이).
 * {@code NAVER} 채널로 등록해 실제 {@code TOSS} 빈과 충돌하지 않는다(라우팅이 NAVER 면 본 dev 빈으로 간다).
 *
 * <p><b>dev 프로파일에서만 활성</b>({@code @Profile("dev")}) — 일반 빌드/테스트/운영 컨텍스트에는 존재하지 않는다.
 * 보상 경로를 관측하려면 {@code externalOrderProductId} 에 "fail" 이 포함되게 트리거하면 confirm 이 예외를 던져
 * step3 실패 → R4 보상으로 진입한다.</p>
 */
@Slf4j
@Component
@Profile("dev")
class DevChannelConfirmStrategy implements ConfirmStrategy {

    @Override
    public Channel channel() {
        return Channel.NAVER;
    }

    @Override
    public void confirm(String externalOrderProductId) {
        if (externalOrderProductId != null && externalOrderProductId.toLowerCase().contains("fail")) {
            log.info("[DEV] channelConfirm 강제 실패 (보상 경로 관측용): orderProductId={}", externalOrderProductId);
            throw new IllegalStateException("[DEV] 강제 외부 통보 실패: " + externalOrderProductId);
        }
        log.info("[DEV] channelConfirm 성공 (외부 PREPARING_PRODUCT 전이 모사): orderProductId={}",
                externalOrderProductId);
    }
}
