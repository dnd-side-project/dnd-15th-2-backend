package com.dnd.qello.answer.web;

import java.time.Clock;
import java.time.Instant;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import com.dnd.qello.answer.service.MediaUploadService;
import com.dnd.qello.answer.service.MediaUploadService.IssueUploadUrlCommand;
import com.dnd.qello.answer.web.request.MediaUploadRequest;
import com.dnd.qello.answer.web.response.MediaConfirmResponse;
import com.dnd.qello.answer.web.response.MediaUploadResponse;
import com.dnd.qello.common.web.response.ApiResponse;
import com.dnd.qello.common.web.response.ApiResponseFactory;

import lombok.RequiredArgsConstructor;

/**
 * 미디어 업로드 web 경계. 소유자와 요청 시각은 각각 JWT subject와 서버 Clock에서
 * 결정하고, 실제 저장소 동작은 MediaUploadService에 위임한다.
 */
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/media-assets")
public class MediaAssetController implements MediaAssetApiSpec {

	private final MediaUploadService mediaUploadService;
	private final ApiResponseFactory responseFactory;
	private final Clock clock;

	@Override
	public ResponseEntity<ApiResponse<MediaUploadResponse>> issueUploadUrl(
		MediaUploadRequest request, Authentication authentication) {
		long userId = authenticatedUserId(authentication);
		Instant requestedAt = Instant.now(clock);
		MediaUploadService.UploadUrl upload = mediaUploadService.issueUploadUrl(
			new IssueUploadUrlCommand(userId, userId, request.contentType(), request.byteSize(), request.checksum(), requestedAt));
		return ResponseEntity.status(HttpStatus.CREATED)
			.body(responseFactory.success(MediaUploadResponse.from(upload)));
	}

	@Override
	public ResponseEntity<ApiResponse<MediaConfirmResponse>> confirm(
		long mediaId, Authentication authentication) {
		MediaConfirmResponse response = MediaConfirmResponse.from(
			mediaUploadService.confirm(mediaId, authenticatedUserId(authentication)));
		return ResponseEntity.ok(responseFactory.success(response));
	}

	private long authenticatedUserId(Authentication authentication) {
		if (authentication == null || authentication.getName() == null) {
			throw unauthorized();
		}
		try {
			long userId = Long.parseLong(authentication.getName());
			if (userId <= 0) {
				throw unauthorized();
			}
			return userId;
		} catch (NumberFormatException exception) {
			throw unauthorized();
		}
	}

	private ResponseStatusException unauthorized() {
		return new ResponseStatusException(HttpStatus.UNAUTHORIZED, "인증 사용자 정보가 유효하지 않습니다");
	}
}
