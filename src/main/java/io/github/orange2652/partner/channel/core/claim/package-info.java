/**
 * core.claim — 클레임(취소/교환/반품) vertical. <b>빈 슬롯</b>(주문 → 클레임 → 상품연동 확장 순).
 *
 * <p>D-1: 주문 vertical 다음 확장 지점. claim 도메인/Port/event 가 들어올 때 nested {@code @ApplicationModule}
 * 을 부여한다. 현재는 패키지 슬롯만 존재.</p>
 */
package io.github.orange2652.partner.channel.core.claim;
