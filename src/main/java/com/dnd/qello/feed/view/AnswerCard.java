package com.dnd.qello.feed.view;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

/**
 * 답변 1건. 질문글 작성자와 그 질문글의 수신 자격자가 조회할 수 있다(2026-08-07
 * 개정, ADR 0002 — 답변 격리 폐기).
 * reactedByMe는 조회하는 뷰어 본인의 공감 여부다 — V8이 answer_reaction의 PK를
 * (answer_id, reactor_id) 복합으로 바꾸면서 볼 수 있는 사람 전원이 각자 공감할 수
 * 있게 됐다. reactionCount는 그 답변이 받은 공감 총수다.
 */
public record AnswerCard(
	long answerId,
	String authorNickname,
	String authorCoarseRegionCode,
	String bodyText,
	List<Long> mediaIds,
	BigDecimal bearingFromSenderDegrees,
	String distanceBand,
	Instant publishedAt,
	Instant editedAt,
	boolean reactedByMe,
	long reactionCount
) {
	public AnswerCard { mediaIds = List.copyOf(mediaIds); }
}
