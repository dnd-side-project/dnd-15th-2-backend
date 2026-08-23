package com.dnd.qello.filtering.web;

import java.time.Instant;

import com.dnd.qello.filtering.domain.FilterVerdict;
import com.dnd.qello.filtering.domain.ManualReviewBand;
import com.dnd.qello.filtering.domain.ManualReviewCase;
import com.dnd.qello.filtering.domain.ManualReviewCaseStatus;
import com.dnd.qello.filtering.domain.ManualReviewPriorityReasonCode;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "수동 검토 건과 결정 결과를 담은 응답입니다.")
public record ManualReviewCaseResponse(
	@Schema(description = "수동 검토 건 식별자입니다.")
	long id,
	@Schema(description = "연결된 필터 작업 식별자입니다.")
	long filterJobId,
	@Schema(description = "검토 건을 만든 검사 설정 식별자입니다.")
	long filterReleaseId,
	@Schema(description = "수동 검토 건의 상태입니다.")
	ManualReviewCaseStatus status,
	@Schema(description = "현재 검토 우선순위 구간입니다.")
	ManualReviewBand band,
	@Schema(description = "검증된 신고 신호의 개수입니다.")
	int validatedReportSignalCount,
	@Schema(description = "검토 우선순위를 계산할 때 사용한 정책 버전입니다.")
	String priorityPolicyVersion,
	@Schema(description = "검토 우선순위를 정한 사유 코드입니다.")
	ManualReviewPriorityReasonCode priorityReasonCode,
	@Schema(description = "검토 결정 시각입니다. 미결정이면 값이 없습니다.")
	Instant resolvedAt,
	@Schema(description = "결정을 내린 운영자 식별자입니다. 미결정이면 값이 없습니다.")
	Long resolvedByOperatorUserId,
	@Schema(description = "검토 결정 결과입니다. 미결정이면 값이 없습니다.")
	FilterVerdict resolvedVerdict,
	@Schema(description = "검토 건이 생성된 시각입니다.")
	Instant createdAt
) {

	public static ManualReviewCaseResponse from(ManualReviewCase reviewCase) {
		return new ManualReviewCaseResponse(
			reviewCase.id(),
			reviewCase.filterJobId(),
			reviewCase.filterReleaseId(),
			reviewCase.status(),
			reviewCase.band(),
			reviewCase.validatedReportSignalCount(),
			reviewCase.priorityPolicyVersion(),
			reviewCase.priorityReasonCode(),
			reviewCase.resolvedAt(),
			reviewCase.resolvedByOperatorUserId(),
			reviewCase.resolvedVerdict(),
			reviewCase.createdAt()
		);
	}
}
