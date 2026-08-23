package com.dnd.qello.direction.web.response;

import java.time.Instant;

import com.dnd.qello.direction.service.DirectionPostService;

import io.swagger.v3.oas.annotations.media.Schema;

/** 비동기 제출 접수 결과. 수신자나 위치와 같은 내부 매칭 값은 포함하지 않는다. */
public record DirectionPostSubmissionResponse(
	@Schema(description = "접수된 질문글의 식별자") long postId,
	@Schema(description = "접수 결과. 항상 SUBMITTED입니다") String submissionStatus,
	@Schema(description = "질문글을 접수한 시각") Instant submittedAt,
	@Schema(description = "이 질문글이 만료되는 시각") Instant expiresAt
) {
	public static DirectionPostSubmissionResponse from(DirectionPostService.SendResult result) {
		return new DirectionPostSubmissionResponse(
			result.post().getId(),
			"SUBMITTED",
			result.post().getSubmittedAt(),
			result.post().getExpiresAt());
	}
}
