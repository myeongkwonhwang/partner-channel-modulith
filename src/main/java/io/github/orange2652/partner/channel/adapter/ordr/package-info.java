/**
 * adapter.ordr — 외부 채널 주문 어댑터 vertical. A1 saga 의 unconfirmedOrder(step1) / channelConfirm(step3)
 * + 외부 보상(seller-cancel) 책임.
 *
 * <p>D-1: adapter 안 도메인별 nested {@code @ApplicationModule}. 주문은 지금, 클레임/송장 연동은 이후 vertical 로
 * 확장. channel_schema 의 staging_order / processed_event 영속(batch 와 같은 schema, 행은 consumer_name 으로
 * 분리). 다른 모듈에는 직접 호출 없이 reply event(R2)만 publish 한다.</p>
 */
@org.springframework.modulith.ApplicationModule(
    displayName = "Channel Adapter :: Order",
    allowedDependencies = { "shared" }
)
package io.github.orange2652.partner.channel.adapter.ordr;
