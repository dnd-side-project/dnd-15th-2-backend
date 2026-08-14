package com.dnd.qello.answer.web.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Positive;

/** 인증 사용자 소유의 이미지 업로드 예약 요청. 소유자는 JWT subject로 결정한다. */
public record MediaUploadRequest(
	@NotBlank(message = "contentType은 필수입니다")
	@Schema(description = "업로드할 이미지 MIME type. image/jpeg, image/jpg 또는 image/png", example = "image/jpeg",
		allowableValues = {"image/jpeg", "image/jpg", "image/png"})
	String contentType,

	@Positive(message = "byteSize는 양수여야 합니다")
	@Schema(description = "업로드할 파일 크기(byte)", example = "524288", minimum = "1",
		requiredMode = Schema.RequiredMode.REQUIRED)
	long byteSize,

	@NotBlank(message = "checksum은 필수입니다")
	@Schema(description = "업로드 파일 checksum")
	String checksum
) {
}
