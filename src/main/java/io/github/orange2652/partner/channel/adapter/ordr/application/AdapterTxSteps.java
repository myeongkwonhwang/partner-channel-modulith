package io.github.orange2652.partner.channel.adapter.ordr.application;

import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * 외부 호출 step 의 DB 작업 Tx 경계 (외부 호출 Tx 분리 전역 컨벤션 — java-spring-expert 자문). step3 channelConfirm
 * 과 그 보상(외부 cancel)이 공유한다.
 *
 * <p>얇은 비-Tx 리스너({@link ChannelConfirmHandler} / {@link ChannelConfirmCompensationHandler})가 본 빈의 메서드를
 * <b>외부 호출 전/후로 나눠</b> 호출한다. 각 메서드는 {@code REQUIRES_NEW} 로 자기 Tx 를 열고 즉시 커밋해, 그 사이의
 * 외부 호출({@code ConfirmStrategy.confirm} / {@code CompensationPort.cancel})이 어떤 Tx 안에도 갇히지 않게 한다
 * (협업원칙 — 외부 호출은 Tx 밖). 별도 빈으로 둔 이유는 self-invocation(같은 클래스 내부 호출은 프록시를 안 거쳐
 * {@code @Transactional} 무효)을 피하기 위함.</p>
 *
 * <ul>
 *   <li>{@link #mark} — 외부 호출 <b>전</b> 멱등 마킹 커밋(정상 경로 중복 외부 WRITE 차단). 실패-재시도/크래시 복구는
 *       R5a timeout scanner + 사전 GET 가드(외부 진실 기반)가 직교로 담당.</li>
 *   <li>{@link #publishReply} — 외부 호출 <b>후</b> reply 발행. {@code publishEvent} 는 반드시 활성 Tx 안이어야 내장
 *       outbox({@code event_publication})에 적재되고 saga 가 수신한다(Tx 밖 발행 시 조용한 유실).</li>
 * </ul>
 */
@Component
@RequiredArgsConstructor
class AdapterTxSteps {

    private final AdapterIdempotencyGuard idempotencyGuard;
    private final ApplicationEventPublisher events;

    /** 외부 호출 전 — 멱등 마킹을 독립 Tx 로 커밋. {@code true}=신규(진행), {@code false}=중복(skip). */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public boolean mark(String consumerName, String eventId) {
        return idempotencyGuard.markIfFirst(consumerName, eventId);
    }

    /** 외부 호출 후 — reply(모듈 event)를 독립 Tx 안에서 발행(outbox 적재). */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void publishReply(Object reply) {
        events.publishEvent(reply);
    }
}
