package com.dnd.qello.feed.view;

import java.time.Instant;

/**
 * `내게 온 질문` 상세. 답변 수와 공감 수는 InboxCard가 담고 있다(2026-08-07 개정).
 */
public record InboxDetail(
	InboxCard card,
	Instant openedAt,
	Instant skipRequestedAt
) { }
