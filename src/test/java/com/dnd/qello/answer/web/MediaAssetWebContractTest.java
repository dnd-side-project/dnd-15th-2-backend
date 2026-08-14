/**
 * Created at: 2026-08-14T14:10:00+09:00
 * Source scenario: TEST-PLAN-GH-122-DIRECTION-PREVIEW-SUBMISSION-API-UNIT-010,
 * UNIT-012; INT-008, INT-017
 */
package com.dnd.qello.answer.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.net.URL;
import java.lang.reflect.RecordComponent;
import java.util.Arrays;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.web.bind.annotation.PostMapping;

import com.dnd.qello.answer.web.request.MediaUploadRequest;
import com.dnd.qello.answer.domain.MediaAsset;
import com.dnd.qello.answer.error.AnswerErrorCode;
import com.dnd.qello.answer.error.AnswerException;
import com.dnd.qello.answer.service.MediaUploadService.UploadUrl;
import com.dnd.qello.answer.service.port.PresignedUpload;
import com.dnd.qello.answer.web.response.MediaConfirmResponse;
import com.dnd.qello.answer.web.response.MediaUploadResponse;

class MediaAssetWebContractTest {

	@Test
	@DisplayName("미디어 API는 ApiSpec과 Controller를 분리한다")
	void keepsApiBoundaryTypesSeparated() {
		assertThat(MediaAssetApiSpec.class.isAssignableFrom(MediaAssetController.class)).isTrue();
		assertThat(MediaUploadRequest.class.getPackageName()).isEqualTo("com.dnd.qello.answer.web.request");
		assertThat(MediaUploadResponse.class.getPackageName()).isEqualTo("com.dnd.qello.answer.web.response");
	}

	@Test
	@DisplayName("업로드 성공 응답에만 presigned URL이 있고 저장소 key는 없다")
	void uploadResponseExposesOnlyPresignedUrl() {
		assertThat(recordComponentNames(MediaUploadResponse.class))
			.containsExactly("mediaId", "uploadUrl", "contentType", "expiresAt");
		assertThat(recordComponentNames(MediaConfirmResponse.class))
			.containsExactly("mediaId", "status");
		assertThat(recordComponentNames(MediaConfirmResponse.class))
			.noneMatch(name -> name.toLowerCase().contains("storage") || name.toLowerCase().contains("key"));
	}

	@Test
	@DisplayName("미디어 HTTP 경계는 upload request와 confirm을 POST로 선언한다")
	void mappingsLiveOnApiSpec() throws Exception {
		assertThat(MediaAssetApiSpec.class.getMethod("issueUploadUrl", MediaUploadRequest.class,
			org.springframework.security.core.Authentication.class).getAnnotation(PostMapping.class).value())
			.containsExactly("/upload-requests");
		assertThat(MediaAssetApiSpec.class.getMethod("confirm", long.class,
			org.springframework.security.core.Authentication.class).getAnnotation(PostMapping.class).value())
			.containsExactly("/{mediaId}/confirm");
	}

	@Test
	@DisplayName("응답 매핑 결과의 미디어 식별자가 없으면 기존 AnswerException으로 변환한다")
	void mapsMissingMediaIdToAnswerException() throws Exception {
		MediaAsset uploading = MediaAsset.upload(1L, "media/1/key", "image/jpeg", 10L, "checksum",
			java.time.Instant.parse("2026-08-14T00:00:00Z"));

		assertThatThrownBy(() -> MediaUploadResponse.from(new UploadUrl(uploading,
			new PresignedUpload(new URL("https://example.invalid/upload"),
				java.time.Instant.parse("2026-08-14T00:10:00Z")))))
			.isInstanceOf(AnswerException.class)
			.hasFieldOrPropertyWithValue("errorCode", AnswerErrorCode.INVALID_ID);
		assertThatThrownBy(() -> MediaConfirmResponse.from(uploading))
			.isInstanceOf(AnswerException.class)
			.hasFieldOrPropertyWithValue("errorCode", AnswerErrorCode.INVALID_ID);
	}

	private static java.util.List<String> recordComponentNames(Class<?> type) {
		return Arrays.stream(type.getRecordComponents()).map(RecordComponent::getName).toList();
	}
}
