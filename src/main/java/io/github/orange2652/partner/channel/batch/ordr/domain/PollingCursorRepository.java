package io.github.orange2652.partner.channel.batch.ordr.domain;

import java.util.Optional;

/**
 * 채널 polling cursor 영속 Port — channel_schema 로컬.
 *
 * <p>{@code (channel, cursorType)} UNIQUE. {@link #findByChannelAndCursorType} 부재 시 호출자가
 * {@link PollingCursor#initial} 로 첫 윈도를 결정한다. 구현은 infra.</p>
 */
public interface PollingCursorRepository {

    PollingCursor save(PollingCursor cursor);

    Optional<PollingCursor> findByChannelAndCursorType(String channel, String cursorType);
}
