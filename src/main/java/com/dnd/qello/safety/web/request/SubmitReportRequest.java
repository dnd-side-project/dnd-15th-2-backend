package com.dnd.qello.safety.web.request;

import com.dnd.qello.safety.domain.ReportReason;
import com.dnd.qello.safety.domain.ReportSubReason;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;

// 신고 접수 요청 본문. subReasonCode·detail·blockAuthor는 선택이다 — 조합
// 유효성은 ReportSubmission이 도메인 레벨에서 검증한다.
public record SubmitReportRequest(
	@NotNull(message = "reasonCode는 필수입니다")
		@Schema(description = "신고 사유 목록에서 선택한 상위 사유.", requiredMode = Schema.RequiredMode.REQUIRED, example = "SPAM_OR_ADVERTISING") ReportReason reasonCode,
		@Schema(description = "선택한 상위 사유에 해당하는 하위 사유. 해당하지 않으면 생략합니다.", example = "CREDIBLE_THREAT") ReportSubReason subReasonCode,
		@Schema(description = "신고에 대한 추가 설명. 기타 사유에는 필수이며 최대 500자입니다.", example = "반복적으로 같은 광고를 보내고 있습니다.") String detail,
		@Schema(description = "신고 대상 작성자를 함께 차단할지 여부.", example = "false") Boolean blockAuthor
) {

	public boolean shouldBlockAuthor() {
		return Boolean.TRUE.equals(blockAuthor);
	}
}
