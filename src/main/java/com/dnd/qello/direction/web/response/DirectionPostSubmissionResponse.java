package com.dnd.qello.direction.web.response;

import java.time.Instant;

import com.dnd.qello.direction.service.DirectionPostService;

/** 비동기 제출 접수 결과. 수신자나 위치와 같은 내부 매칭 값은 포함하지 않는다. */
public record DirectionPostSubmissionResponse(
	long postId,
	String submissionStatus,
	Instant submittedAt,
	Instant expiresAt
) {
	public static DirectionPostSubmissionResponse from(DirectionPostService.SendResult result) {
		return new DirectionPostSubmissionResponse(
			result.post().getId(),
			"SUBMITTED",
			result.post().getSubmittedAt(),
			result.post().getExpiresAt());
	}
}
