package com.dnd.qello.question.web.response;

import java.time.Instant;

import com.dnd.qello.question.domain.QuestionProposalReview;

import io.swagger.v3.oas.annotations.media.Schema;

/** 제안에 대한 운영자 판정 한 건. append-only 이력이므로 수정 시각이 없다. */
@Schema(description = "질문 제안에 대한 운영자 판정 한 건을 담는 응답입니다.")
public record QuestionProposalReviewResponse(
	@Schema(description = "판정 이력 식별자입니다.")
	long id,
	@Schema(description = "판정한 질문 제안 식별자입니다.")
	long proposalId,
	@Schema(description = "판정한 운영자 계정 식별자입니다.")
	long reviewerId,
	@Schema(description = "판정 결과입니다. 반려 결과에서는 REJECTED입니다.")
	String decision,
	@Schema(description = "반려 사유입니다. 승인 판정에서는 null입니다.")
	String reason,
	@Schema(description = "판정한 시각입니다.")
	Instant reviewedAt
) {
	public static QuestionProposalReviewResponse from(QuestionProposalReview review) {
		return new QuestionProposalReviewResponse(
			review.getId(),
			review.getProposalId(),
			review.getReviewerId(),
			review.getDecision().name(),
			review.getReason(),
			review.getReviewedAt());
	}
}
