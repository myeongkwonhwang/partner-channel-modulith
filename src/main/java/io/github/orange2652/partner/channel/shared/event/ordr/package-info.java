/**
 * A1 (주문) SAGA 이벤트 계약 — orderReception 트리거 + step command/reply + 보상 record.
 *
 * <p>모듈 간 통신은 본 패키지의 event record 로만 한다 (직접 호출 금지). saga 모듈이 command 를 발행하고
 * 책임 모듈(adapter/core)이 수신·처리 후 reply 를 발행한다. {@code OrderReceivedEvent} 만
 * {@code @Externalized} 로 외부 Kafka 에도 발행되며(외부 알림 겸 in-VM sagaStart 입력), 나머지는 in-VM
 * Application Event 다.</p>
 *
 * <p><b>상관 키</b>: MSA 는 Kafka header 로 sagaId 를 운반했으나, in-VM event 인 modulith 는 {@code sagaId}
 * 를 payload 에 명시한다. 멱등 키는 {@code "{channel}:{externalOrderProductId}"} ({@code EventKey}).</p>
 */
package io.github.orange2652.partner.channel.shared.event.ordr;
