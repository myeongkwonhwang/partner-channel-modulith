/**
 * serviceCore 모듈 — 자사 도메인 (주문 + 송장) 책임.
 *
 * <p>MSA 의 services/service-core 와 1:1 매핑. SAGA 의 validate / confirmedOrder (Pivot ★) / persistInvoice /
 * dispatchToChannel step 책임. core_schema 의 orders / invoices / processed_event 영속.
 * 자사 LogisticsGateway 호출 (서비스 내부 — 외부 채널 아님).
 */
@org.springframework.modulith.ApplicationModule(
    displayName = "Service Core",
    allowedDependencies = { "shared" }
)
package io.github.orange2652.partner.channel.core;
