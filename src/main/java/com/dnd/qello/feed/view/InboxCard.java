package com.dnd.qello.feed.view;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

import com.dnd.qello.direction.domain.PostRecipientStatus;

/**
 * `내게 온 질문` 카드 1장.
 * 답변 수와 공감 수를 의도적으로 포함하지 않는다 — 받은 사람에게 그 숫자를 보여주지
 * 않는 것이 제품 규칙이다(docs/adr/0001-답변은-질문자에게만-도달한다.md).
 * 필드를 두지 않음으로써 이후에 실수로 채울 수 없게 한다.
 */
public record InboxCard(
	long postRecipientId,
	long postId,
	PostRecipientStatus status,
	String questionText,
	String bodyText,
	List<Long> mediaIds,
	String senderCoarseRegionCode,
	BigDecimal matchedBearingDegrees,
	String distanceBand,
	Instant matchedAt,
	Instant expiresAt
) {
	public InboxCard { mediaIds = List.copyOf(mediaIds); }
}
