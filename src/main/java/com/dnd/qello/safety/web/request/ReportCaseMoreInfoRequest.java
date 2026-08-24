package com.dnd.qello.safety.web.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** 추가 정보 요청(MORE_INFO_REQUIRED)에 필요한 운영자 입력. */
public record ReportCaseMoreInfoRequest(
	@NotBlank(message = "internalNote는 필수입니다")
	@Size(max = 2_000, message = "internalNote는 2000자를 초과할 수 없습니다")
		@Schema(description = "무엇을 더 확인해야 하는지 남기는 운영자 내부 메모. 신고자에게 공개하지 않습니다.", example = "원본 미디어 확인이 더 필요함")
	String internalNote
) {
}
