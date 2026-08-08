package com.dnd.qello.feed.view;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

import com.dnd.qello.direction.domain.PostRecipientStatus;

/**
 * `내게 온 질문` 카드 1장.
 * 2026-08-07 개정(ADR 0002)으로 답변이 수신 자격자 전원에게 공개되면서
 * answerCount/reactionCount/unreadAnswerCount를 노출한다 — "받은 사람에게 숫자를
 * 보여주지 않는다"던 옛 제품 규칙은 폐기됐다.
 * inboundBearingDegrees는 수신자 기준 방향이다(발송자 기준 matched_bearing_deg가
 * 아니다) — 화면에 표시하는 방향은 언제나 이 값에서 파생한다.
 * distanceM과 distanceBand는 상호 배타적이다 — 근거리 하한 미만이면 distanceM이
 * null이고 distanceBand만 채워지며, 하한 이상이면 반대다. 판정은 SQL이 하므로 여기서는
 * 값을 그대로 옮긴다.
 */
public record InboxCard(
	long postRecipientId,
	long postId,
	PostRecipientStatus status,
	String questionText,
	String bodyText,
	List<Long> mediaIds,
	String senderCoarseRegionCode,
	BigDecimal inboundBearingDegrees,
	Long distanceM,
	String distanceBand,
	Instant matchedAt,
	Instant expiresAt,
	long answerCount,
	long reactionCount,
	long unreadAnswerCount
) {
	public InboxCard { mediaIds = List.copyOf(mediaIds); }
}
