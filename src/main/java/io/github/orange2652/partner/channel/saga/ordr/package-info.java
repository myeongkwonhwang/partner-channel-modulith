/**
 * saga.ordr — A1(주문) saga 오케스트레이션 vertical. saga 인스턴스 수명(state) + step advance + 보상/reconciliation
 * 의 상태 보유.
 *
 * <p>D-1: saga 안 도메인별 nested {@code @ApplicationModule}. A1(주문)은 지금, B1(송장)은 이후 vertical 로 확장.
 * saga_schema 의 saga_state / processed_event 영속. Phase 1 은 상태 도메인 + Port + infra 골격만 두고,
 * sagaStart in-VM {@code @ApplicationModuleListener} + step command/reply 오케스트레이션 배선은 Phase 2 다.
 * 다른 모듈에는 직접 호출 없이 command/reply event(R2)로만 통신한다.</p>
 */
@org.springframework.modulith.ApplicationModule(
    displayName = "Saga Orchestrator :: Order",
    allowedDependencies = { "shared" }
)
package io.github.orange2652.partner.channel.saga.ordr;
