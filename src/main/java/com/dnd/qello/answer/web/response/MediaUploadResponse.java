package com.dnd.qello.answer.web.response;

import java.time.Instant;

import com.dnd.qello.answer.error.AnswerErrorCode;
import com.dnd.qello.answer.error.AnswerException;
import com.dnd.qello.answer.service.MediaUploadService;

/** presigned URL이 필요한 업로드 예약 성공 응답. storage key는 노출하지 않는다. */
public record MediaUploadResponse(
	long mediaId,
	String uploadUrl,
	String contentType,
	Instant expiresAt
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
