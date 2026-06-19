/**
 * batch.ordr — 외부 채널 주문 polling vertical. A1 saga 의 외부 트리거(orderReception) 책임.
 *
 * <p>D-1: batch 안 도메인별 nested {@code @ApplicationModule}. 주문 polling 은 지금, 송장 수신 polling 은
 * 이후 vertical 로 확장. channel_schema 의 polling_cursor / processed_event 영속. 다른 모듈에는 직접 호출
 * 없이 {@code OrderReceivedEvent}(R1) 만 publish 한다.</p>
 */
@org.springframework.modulith.ApplicationModule(
    displayName = "Channel Batch :: Order",
    allowedDependencies = { "shared" }
)
package io.github.orange2652.partner.channel.batch.ordr;
