package com.dnd.qello.answer.web.response;

import java.time.Instant;

import com.dnd.qello.answer.error.AnswerErrorCode;
import com.dnd.qello.answer.error.AnswerException;
import com.dnd.qello.answer.service.MediaUploadService;

import io.swagger.v3.oas.annotations.media.Schema;

/** presigned URL이 필요한 업로드 예약 성공 응답. storage key는 노출하지 않는다. */
public record MediaUploadResponse(
	@Schema(description = "발급된 이미지 식별자. 업로드 확인과 첨부에 씁니다") long mediaId,
	@Schema(description = "이미지 파일을 PUT으로 올릴 임시 주소") String uploadUrl,
	@Schema(description = "이 주소로 올릴 수 있는 이미지 형식") String contentType,
	@Schema(description = "이 주소를 쓸 수 있는 마지막 시각") Instant expiresAt
) {
	public static MediaUploadResponse from(MediaUploadService.UploadUrl upload) {
		return new MediaUploadResponse(
			requireMediaId(upload.asset().getId()),
			upload.presignedUpload().url().toExternalForm(),
			upload.asset().getMimeType(),
			upload.presignedUpload().expiresAt());
	}

	private static long requireMediaId(Long mediaId) {
		if (mediaId == null || mediaId <= 0) {
			throw new AnswerException(AnswerErrorCode.INVALID_ID, "mediaId", "업로드 예약 결과의 mediaId가 유효하지 않습니다");
		}
		return mediaId;
	}
}
