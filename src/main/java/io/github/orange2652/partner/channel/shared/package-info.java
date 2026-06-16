/**
 * shared 모듈 — 공통 도메인 (Channel enum / PartnerChannelException 계층) + 공통 인프라 (Kafka 헤더 추출 / 멱등 가드 / DLQ 설정).
 *
 * <p>MSA 의 libs/common-domain + libs/kafka-infra 일부 + libs/channel-event-schema 의 record DTO 와 매핑.
 * 모든 모듈이 의존 가능 (open module).
 */
@org.springframework.modulith.ApplicationModule(
    displayName = "Shared",
    type = org.springframework.modulith.ApplicationModule.Type.OPEN
)
package io.github.orange2652.partner.channel.shared;
