/**
 * channelBatch 모듈 — 외부 채널 (토스 / 쿠팡 / 네이버 / 유튜브쇼핑) polling + 자사 LogisticsGateway internal polling.
 *
 * <p>MSA 의 services/channel-batch 와 1:1 매핑. SAGA 외부 트리거 (orderReception, invoiceReceipt) 책임.
 * 다른 모듈에는 Application Events 만 publish — 직접 호출 없음.
 */
@org.springframework.modulith.ApplicationModule(
    displayName = "Channel Batch",
    allowedDependencies = { "shared" }
)
package io.github.orange2652.partner.channel.batch;
