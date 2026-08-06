package com.dnd.qello.feed.view;

import java.time.Instant;

/**
 * `내게 온 질문` 상세.
 * InboxCard와 같은 이유로 답변 수와 공감 수를 포함하지 않는다.
 */
public record InboxDetail(
	InboxCard card,
	Instant openedAt,
	Instant skipRequestedAt
) { }
