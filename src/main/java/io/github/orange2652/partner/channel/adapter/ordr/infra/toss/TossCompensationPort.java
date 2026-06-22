package io.github.orange2652.partner.channel.adapter.ordr.infra.toss;

import io.github.orange2652.partner.channel.adapter.ordr.application.CompensationPort;
import io.github.orange2652.partner.channel.adapter.ordr.domain.ManualCancelRequiredException;
import io.github.orange2652.partner.channel.adapter.ordr.domain.TossApiException;
import io.github.orange2652.partner.channel.shared.domain.CancelReason;
import io.github.orange2652.partner.channel.shared.domain.Channel;
import io.github.orange2652.partner.channel.shared.domain.ExternalOrderStatus;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * 토스 {@link CompensationPort} 구현 (R6 D6) — R4 보상의 외부 seller-cancel
 * ({@code POST /order-products/{id}/seller-cancel}).
 *
 * <p><b>사전 GET 가드</b>: 취소 전 {@link TossOrderQueryAdapter} 로 현재 상태를 확인한다(재호출 멱등[미확인] 흡수).
 * 이미 {@link ExternalOrderStatus#CANCELED} 면 NoOp(멱등). 그 외엔 seller-cancel 호출(귀책 {@link CancelReason#charger}).</p>
 *
 * <p><b>실패 분류</b>(R4): transient {@link TossApiException}(TOO_MANY_REQUEST/5xx)는 그대로 전파 → 핸들러 FAILED
 * (재시도). 비-transient(INVALID_REQUEST 등 재시도 무의미)는 {@link ManualCancelRequiredException} 으로 변환 →
 * 핸들러 MANUAL_REQUIRED → saga PENDING_MANUAL_CANCEL(운영 개입). 외부 호출은 호출자 Tx 밖.</p>
 */
@Slf4j
@Component
@RequiredArgsConstructor
class TossCompensationPort implements CompensationPort {

    /** 토스 seller-cancel body 길이 한도 (R6 스펙). 초과분은 절단한다(CancelReason 계약). */
    private static final int REASON_MAX = 100;
    private static final int DETAIL_REASON_MAX = 250;

    private final TossApiClient client;
    private final TossOrderQueryAdapter queryAdapter;

    @Override
    public Channel channel() {
        return Channel.TOSS;
    }

    @Override
    public void cancel(String externalOrderProductId, CancelReason reason) {
        ExternalOrderStatus current = queryAdapter.queryStatus(externalOrderProductId);
        if (current == ExternalOrderStatus.CANCELED) {
            log.info("토스 cancel 멱등 NoOp (이미 취소됨): orderProductId={}", externalOrderProductId);
            return;
        }

        long id = TossApiClient.parseOrderProductId(externalOrderProductId);
        try {
            client.sellerCancel(id, new TossApiClient.SellerCancelBody(
                    reason.charger().name(),
                    truncate(reason.reason(), REASON_MAX),
                    truncate(reason.detailReason(), DETAIL_REASON_MAX)));
        } catch (TossApiException e) {
            if (e.isTransient()) {
                throw e;
            }
            throw new ManualCancelRequiredException(externalOrderProductId, "토스 cancel 거부: " + e.getMessage());
        }
        log.info("토스 seller-cancel 완료: orderProductId={} charger={}", externalOrderProductId, reason.charger());
    }

    /** 토스 길이 한도 초과분 절단(null 은 그대로). */
    private static String truncate(String value, int max) {
        if (value == null || value.length() <= max) {
            return value;
        }
        return value.substring(0, max);
    }
}

