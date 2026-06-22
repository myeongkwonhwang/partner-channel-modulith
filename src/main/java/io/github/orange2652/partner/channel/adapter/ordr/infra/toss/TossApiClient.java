package io.github.orange2652.partner.channel.adapter.ordr.infra.toss;

import io.github.orange2652.partner.channel.adapter.ordr.domain.TossApiException;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.function.Supplier;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestClient;

/**
 * 토스 ShoppingFep API 저수준 HTTP seam (R6 D6) — 주문조회/상태변경/판매자취소를 타입 메서드로 노출한다. Port 어댑터
 * (TossOrderQueryAdapter/Strategy)가 본 client 를 통해 호출하고, 단위 테스트는 본 client 를 mock 한다(매핑·가드 로직
 * 검증). 본 client 자체의 실호출은 sandbox 통합 단계 이연(단위 미커버).
 *
 * <p><b>토스 함정</b>: 오류가 HTTP 200 + {@code resultType=FAIL}({@code error.errorCode})로 온다 —
 * {@code TOO_MANY_REQUEST} 포함. 따라서 200 을 무조건 성공으로 보면 안 되고 {@code resultType} 을 검사해
 * {@link TossApiException}(transient 구분)으로 변환한다. 401(토큰 무효)은 {@link TossTokenManager#invalidate()}
 * 후 1회 재시도한다.</p>
 */
@Slf4j
@Component
class TossApiClient {

    private static final String RESULT_FAIL = "FAIL";
    private static final String ERR_TOO_MANY = "TOO_MANY_REQUEST";

    private final RestClient restClient;
    private final TossTokenManager tokenManager;
    private final TossProperties properties;

    TossApiClient(TossTokenManager tokenManager, TossProperties properties) {
        this.tokenManager = tokenManager;
        this.properties = properties;
        this.restClient = RestClient.builder().baseUrl(properties.baseUrl()).build();
    }

    /**
     * 단건 주문상품의 현재 상태를 조회한다. 토스는 단건 GET 이 없어 {@code /orders/v2} 를 lookback 기간으로 cursor
     * 페이지네이션하며 {@code orderProductId} 를 찾는다(R6 제약 — 비효율, 일괄 최적화는 Phase 4).
     *
     * @return 찾으면 상태, 기간 내 없으면 empty
     */
    Optional<TossOrderStatus> findOrderProductStatus(long orderProductId) {
        LocalDate end = today();
        LocalDate start = end.minusDays(properties.lookbackDaysOrDefault());
        String cursor = null;
        do {
            final String pageCursor = cursor;
            OrderListResponse page = withAuthRetry(() -> restClient.get()
                    .uri(uri -> uri.path("/orders/v2")
                            .queryParam("startDate", start)
                            .queryParam("endDate", end)
                            .queryParamIfPresent("nextCursor", Optional.ofNullable(pageCursor))
                            .build())
                    .header("Authorization", bearer())
                    .retrieve()
                    .body(OrderListResponse.class));
            requireBody(page);
            verifyOk(page.resultType(), page.error());

            List<OrderProductItem> orders = page.success() == null ? List.of() : page.success().orders();
            for (OrderProductItem item : orders) {
                if (item.orderProductId() == orderProductId) {
                    return TossOrderStatus.fromCode(item.orderProductStatus());
                }
            }
            cursor = page.success() == null ? null : page.success().nextCursor();
        } while (cursor != null);
        return Optional.empty();
    }

    /** 주문상품 상태 변경(PUT). 단건이라도 토스는 배열 — 1원소로 호출. 부분성공 결과를 반환한다. */
    ChangeStatusResult changeProductStatus(List<Long> orderProductIds, TossOrderStatus status) {
        ChangeStatusResponse response = withAuthRetry(() -> restClient.put()
                .uri("/orders/products/status")
                .header("Authorization", bearer())
                .contentType(MediaType.APPLICATION_JSON)
                .body(new ChangeStatusRequest(orderProductIds, status.name()))
                .retrieve()
                .body(ChangeStatusResponse.class));
        requireBody(response);
        verifyOk(response.resultType(), response.error());
        ChangeStatusSuccess s = response.success();
        return new ChangeStatusResult(
                s == null ? 0 : s.totalCount(),
                s == null ? 0 : s.failedCount(),
                s == null || s.failedReasons() == null ? List.of() : s.failedReasons());
    }

    /** 판매자 취소(POST seller-cancel). 단건. */
    void sellerCancel(long orderProductId, SellerCancelBody body) {
        SimpleResponse response = withAuthRetry(() -> restClient.post()
                .uri("/order-products/{id}/seller-cancel", orderProductId)
                .header("Authorization", bearer())
                .contentType(MediaType.APPLICATION_JSON)
                .body(body)
                .retrieve()
                .body(SimpleResponse.class));
        requireBody(response);
        verifyOk(response.resultType(), response.error());
    }

    /** externalOrderProductId(토스 orderProductId, int64 문자열) → long. 형식 오류는 비-transient 예외. */
    static long parseOrderProductId(String externalOrderProductId) {
        try {
            return Long.parseLong(externalOrderProductId);
        } catch (NumberFormatException e) {
            throw new TossApiException("토스 orderProductId 형식 오류: " + externalOrderProductId, false);
        }
    }

    private String bearer() {
        return "Bearer " + tokenManager.bearerToken();
    }

    /** 401 이면 토큰 무효화 후 1회 재시도. */
    private <T> T withAuthRetry(Supplier<T> call) {
        try {
            return call.get();
        } catch (HttpClientErrorException.Unauthorized e) {
            log.info("토스 401 → 토큰 재발급 후 1회 재시도");
            tokenManager.invalidate();
            return call.get();
        } catch (HttpClientErrorException e) {
            throw new TossApiException("토스 HTTP 오류: status=" + e.getStatusCode(), e.getStatusCode().is5xxServerError());
        }
    }

    /** 응답 envelope 자체가 null 이면(비정상 응답) transient 예외 — 이후 success() 역참조 NPE 방지. */
    private void requireBody(Object envelope) {
        if (envelope == null) {
            throw new TossApiException("토스 응답 본문 없음", true);
        }
    }

    /** resultType=FAIL 이면 errorCode 로 transient 구분해 예외. TOO_MANY_REQUEST=transient. */
    private void verifyOk(String resultType, ErrorBody error) {
        if (RESULT_FAIL.equals(resultType)) {
            String code = error == null ? null : error.errorCode();
            boolean isTransient = ERR_TOO_MANY.equals(code);
            throw new TossApiException("토스 API 실패: errorCode=" + code, isTransient);
        }
    }

    private LocalDate today() {
        return LocalDate.now();
    }

    // --- 외부로 노출하는 결과/요청 타입 ---

    /** 상태변경 부분성공 결과. */
    record ChangeStatusResult(int totalCount, int failedCount, List<String> failedReasons) {
        boolean hasFailure() {
            return failedCount > 0;
        }
    }

    /** seller-cancel 요청 body(토스 스키마). */
    record SellerCancelBody(String deliveryPenaltyCharger, String reason, String detailReason) {
    }

    // --- 토스 응답/요청 DTO (envelope) ---

    private record OrderListResponse(String resultType, ErrorBody error, OrderListSuccess success) {
    }

    private record OrderListSuccess(List<OrderProductItem> orders, String nextCursor) {
    }

    private record OrderProductItem(long orderProductId, String orderProductStatus) {
    }

    private record ChangeStatusRequest(List<Long> orderProductIds, String status) {
    }

    private record ChangeStatusResponse(String resultType, ErrorBody error, ChangeStatusSuccess success) {
    }

    private record ChangeStatusSuccess(int totalCount, int failedCount, List<String> failedReasons) {
    }

    private record SimpleResponse(String resultType, ErrorBody error) {
    }

    private record ErrorBody(String errorCode, String reason) {
    }
}
