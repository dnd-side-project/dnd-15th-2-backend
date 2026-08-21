package com.dnd.qello.safety.web.response;

import java.time.Instant;

import com.dnd.qello.safety.domain.ReportCase;

// 운영자 전용 사건 응답. internal_note는 여기 없다 — moderation_review 자체가
// 판정 API 요청/응답에 실리지 않는다(완료 조건: 운영자 응답에만 internal_note가
// 포함된다는 것은 신고자 응답과의 대비이지, 이 응답에 감사 기록 원문을 되돌려
// 준다는 뜻이 아니다).
public record ReportCaseResponse(
	long id,
	Long targetUserId,
	Long directionPostId,
	Long answerId,
	String status,
	String severity,
	String queue,
	String decision,
	Instant createdAt,
	Instant resolvedAt,
	Instant slaDueAt,
	boolean overdue,
	Long linkedManualReviewCaseId
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
