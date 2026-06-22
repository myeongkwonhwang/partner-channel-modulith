package io.github.orange2652.partner.channel.adapter.ordr.infra.toss;

import io.github.orange2652.partner.channel.adapter.ordr.application.ConfirmStrategy;
import io.github.orange2652.partner.channel.adapter.ordr.domain.TossApiException;
import io.github.orange2652.partner.channel.shared.domain.Channel;
import io.github.orange2652.partner.channel.shared.domain.ExternalOrderStatus;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * 토스 {@link ConfirmStrategy} 구현 (R6 D6) — A1 step3. PAID → PREPARING_PRODUCT 전이로 주문 수락을 통보한다
 * ({@code PUT /orders/products/status}, 단건이라도 1원소 배열).
 *
 * <p><b>사전 GET 가드</b>: 전이 전 {@link TossOrderQueryAdapter} 로 현재 상태를 확인한다(재호출 멱등[미확인] 흡수,
 * R6 D6). 이미 {@link ExternalOrderStatus#ACCEPTED}(준비중)면 NoOp 성공(멱등). {@link ExternalOrderStatus#PAID}
 * 일 때만 전이. 그 외(CANCELED/OTHER)는 confirm 불가라 예외 → 호출 핸들러가 reply.failed → R4 보상.</p>
 *
 * <p><b>부분성공</b>: 토스 응답 {@code failedCount>0} 이면 예외로 변환한다(1건이라 0/1). 외부 호출은 호출자 Tx 밖
 * (handler 가 보장).</p>
 */
@Slf4j
@Component
@RequiredArgsConstructor
class TossConfirmStrategy implements ConfirmStrategy {

    private final TossApiClient client;
    private final TossOrderQueryAdapter queryAdapter;

    @Override
    public Channel channel() {
        return Channel.TOSS;
    }

    @Override
    public void confirm(String externalOrderProductId) {
        ExternalOrderStatus current = queryAdapter.queryStatus(externalOrderProductId);
        if (current == ExternalOrderStatus.ACCEPTED) {
            log.info("토스 confirm 멱등 NoOp (이미 준비중): orderProductId={}", externalOrderProductId);
            return;
        }
        if (current != ExternalOrderStatus.PAID) {
            throw new TossApiException(
                    "confirm 불가 상태(PAID 아님): orderProductId=" + externalOrderProductId + " status=" + current,
                    false);
        }

        long id = TossApiClient.parseOrderProductId(externalOrderProductId);
        TossApiClient.ChangeStatusResult result =
                client.changeProductStatus(List.of(id), TossOrderStatus.PREPARING_PRODUCT);
        if (result.hasFailure()) {
            throw new TossApiException(
                    "토스 상태변경 실패: orderProductId=" + externalOrderProductId + " reasons=" + result.failedReasons(),
                    false);
        }
        log.info("토스 confirm 완료 (PREPARING_PRODUCT): orderProductId={}", externalOrderProductId);
    }
}
