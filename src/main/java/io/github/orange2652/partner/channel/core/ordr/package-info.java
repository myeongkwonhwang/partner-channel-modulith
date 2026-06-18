/**
 * core.ordr — 자사 주문(order) vertical. A1 saga 의 validate(step2) / confirmedOrder(step4 Pivot) 책임.
 *
 * <p>D-1: core 안 도메인별 nested {@code @ApplicationModule}. 주문은 지금, claim/invc 는 빈 슬롯으로 확장 대기.
 * core_schema 의 orders / processed_event 영속 + 자사 LogisticsGateway 호출(외부 채널 아님).</p>
 */
@org.springframework.modulith.ApplicationModule(
    displayName = "Core :: Order",
    allowedDependencies = { "shared" }
)
package io.github.orange2652.partner.channel.core.ordr;
