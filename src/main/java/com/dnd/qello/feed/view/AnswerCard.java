package com.dnd.qello.feed.view;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

/**
 * 답변 1건. 질문자만 조회할 수 있다.
 * reactedByMe는 질문자 본인의 공감 여부다 — answer_reaction은 PK가 answer_id 하나여서
 * 답변당 최대 한 행이고, 그 한 행의 주체는 질문자뿐이다.
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
	boolean reactedByMe
) {
	public AnswerCard { mediaIds = List.copyOf(mediaIds); }
}
