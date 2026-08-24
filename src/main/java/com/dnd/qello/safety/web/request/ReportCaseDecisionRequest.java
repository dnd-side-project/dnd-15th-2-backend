package com.dnd.qello.safety.web.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/** 사건 판정(ACTIONED/NO_VIOLATION)에 필요한 운영자 입력. */
public record ReportCaseDecisionRequest(
	@NotNull(message = "decision은 필수입니다")
		@Schema(description = "사건을 종결할 최종 판정. ACTIONED 또는 NO_VIOLATION만 허용합니다.", example = "ACTIONED")
	String decision,

	@Size(max = 2_000, message = "internalNote는 2000자를 초과할 수 없습니다")
		@Schema(description = "판정 근거를 남기는 운영자 내부 메모. 신고자에게 공개하지 않습니다.", example = "명백한 스팸으로 판단해 콘텐츠를 숨김")
	String internalNote
) {
}
