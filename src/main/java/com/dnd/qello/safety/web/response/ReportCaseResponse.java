package com.dnd.qello.safety.web.response;

import java.time.Instant;

import com.dnd.qello.safety.domain.ReportCase;

import io.swagger.v3.oas.annotations.media.Schema;

// 운영자 전용 사건 응답. internal_note는 여기 없다 — moderation_review 자체가
// 판정 API 요청/응답에 실리지 않는다(완료 조건: 운영자 응답에만 internal_note가
// 포함된다는 것은 신고자 응답과의 대비이지, 이 응답에 감사 기록 원문을 되돌려
// 준다는 뜻이 아니다).
public record ReportCaseResponse(
	@Schema(description = "신고 사건의 식별자.") long id,
	@Schema(description = "신고 대상 사용자의 식별자. 대상이 사용자가 아니면 null입니다.") Long targetUserId,
	@Schema(description = "신고 대상 질문글의 식별자. 대상이 질문글이 아니면 null입니다.") Long directionPostId,
	@Schema(description = "신고 대상 답변의 식별자. 대상이 답변이 아니면 null입니다.") Long answerId,
	@Schema(description = "신고 사건의 처리 상태.") String status,
	@Schema(description = "신고 사건의 심각도.") String severity,
	@Schema(description = "신고 사건이 속한 처리 대기열.") String queue,
	@Schema(description = "종결된 사건의 최종 판정. 아직 종결되지 않았으면 null입니다.") String decision,
	@Schema(description = "신고 사건이 생성된 시각.") Instant createdAt,
	@Schema(description = "신고 사건이 종결된 시각. 아직 종결되지 않았으면 null입니다.") Instant resolvedAt,
	@Schema(description = "SLA상 사건 처리가 완료되어야 하는 시각.") Instant slaDueAt,
	@Schema(description = "SLA 마감 시각이 현재 시각보다 지났는지 여부.") boolean overdue,
	@Schema(description = "연결된 수동 검토 사건의 식별자. 없으면 null입니다.") Long linkedManualReviewCaseId
) {
	public static ReportCaseResponse from(ReportCase reportCase, Instant now) {
		return new ReportCaseResponse(
			reportCase.id(),
			reportCase.targetUserId(),
			reportCase.directionPostId(),
			reportCase.answerId(),
			reportCase.status().name(),
			reportCase.severity().name(),
			reportCase.queue().name(),
			reportCase.decision() == null ? null : reportCase.decision().name(),
			reportCase.createdAt(),
			reportCase.resolvedAt(),
			reportCase.slaDueAt(),
			reportCase.slaDueAt().isBefore(now),
			reportCase.linkedManualReviewCaseId());
	}
}
