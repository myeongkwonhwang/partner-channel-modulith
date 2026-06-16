/**
 * channelAdapter 모듈 — 외부 채널 API 호출 어댑터 + staging 정규화.
 *
 * <p>MSA 의 services/channel-adapter 와 1:1 매핑. SAGA 의 unconfirmedOrder step (외부 PAID→PREPARING_PRODUCT 전이
 * + staging INSERT) + 보상 (seller-cancel) 책임. saga module 의 이벤트 수신 + reply event 발행.
 */
@org.springframework.modulith.ApplicationModule(
    displayName = "Channel Adapter",
    allowedDependencies = { "shared" }
)
package io.github.orange2652.partner.channel.adapter;
