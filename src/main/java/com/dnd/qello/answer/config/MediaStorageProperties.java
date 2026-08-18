package com.dnd.qello.answer.config;

import java.time.Duration;
import java.util.Set;
import java.util.stream.Collectors;

import org.springframework.boot.context.properties.ConfigurationProperties;

import com.dnd.qello.answer.error.AnswerErrorCode;
import com.dnd.qello.answer.error.AnswerException;
import com.dnd.qello.answer.domain.ImageMimeType;

/**
 * presigned URL 발급 정책. 이미지 입력은 JPEG/JPG(image/jpeg)와 PNG(image/png)만
 * 허용한다. 실제 버킷 이름과 리전은 환경별 값이므로 여기 기본값을 두지 않고 프로필별
 * properties에서 주입한다(local: 실제 dev 버킷, test: LocalStack 버킷).
 */
@ConfigurationProperties(prefix = "qello.media")
public record MediaStorageProperties(String bucket, Set<String> allowedMimeTypes, long maxByteSize,
	Duration uploadUrlTtl, Duration viewUrlTtl, String defaultProfileImageKey) {

	public MediaStorageProperties {
		if (bucket == null || bucket.isBlank()) {
			throw new AnswerException(AnswerErrorCode.REQUIRED_VALUE_MISSING, "bucket",
				"qello.media.bucket은 필수입니다");
		}
		if (allowedMimeTypes == null || allowedMimeTypes.isEmpty()) {
			throw new AnswerException(AnswerErrorCode.REQUIRED_VALUE_MISSING, "allowedMimeTypes",
				"qello.media.allowed-mime-types는 최소 1개 이상이어야 합니다");
		}
		Set<String> canonicalMimeTypes = allowedMimeTypes.stream()
			.map(MediaStorageProperties::canonicalConfiguredMimeType)
			.collect(Collectors.toUnmodifiableSet());
		if (!ImageMimeType.supportedMimeTypes().equals(canonicalMimeTypes)) {
			throw new AnswerException(AnswerErrorCode.INVALID_MEDIA_METADATA, "allowedMimeTypes",
				"qello.media.allowed-mime-types는 JPEG/PNG만 지원합니다");
		}
		allowedMimeTypes = canonicalMimeTypes;
		if (maxByteSize <= 0) {
			throw new AnswerException(AnswerErrorCode.INVALID_MEDIA_METADATA, "maxByteSize",
				"qello.media.max-byte-size는 양수여야 합니다");
		}
		if (uploadUrlTtl == null || uploadUrlTtl.isZero() || uploadUrlTtl.isNegative()) {
			throw new AnswerException(AnswerErrorCode.INVALID_MEDIA_METADATA, "uploadUrlTtl",
				"qello.media.upload-url-ttl은 양수여야 합니다");
		}
		// 업로드 TTL과 분리한다. 업로드 TTL은 최대 크기 PUT 하나가 느린 회선에서 끝날 시간을
		// 재지만, 조회 URL의 수명은 그대로 노출 창이 된다. 두 값을 묶으면 업로드 TTL을 늘릴 때
		// 조회 URL 수명이 함께 늘어난다.
		if (viewUrlTtl == null || viewUrlTtl.isZero() || viewUrlTtl.isNegative()) {
			throw new AnswerException(AnswerErrorCode.INVALID_MEDIA_METADATA, "viewUrlTtl",
				"qello.media.view-url-ttl은 양수여야 합니다");
		}
		// 값이 없으면 프로필 이미지를 설정하지 않은 모든 사용자의 조회가 깨진다.
		// 누락을 런타임까지 미루지 않고 기동에서 끊는다.
		if (defaultProfileImageKey == null || defaultProfileImageKey.isBlank()) {
			throw new AnswerException(AnswerErrorCode.REQUIRED_VALUE_MISSING, "defaultProfileImageKey",
				"qello.media.default-profile-image-key는 필수입니다");
		}
	}

	public boolean isAllowedMimeType(String mimeType) {
		String canonical = canonicalMimeType(mimeType);
		return canonical != null && allowedMimeTypes.contains(canonical);
	}

	/**
	 * 클라이언트가 보내는 JPG 별칭을 저장소가 서명하는 표준 MIME으로 통일한다.
	 * 파일 확장자는 신뢰하지 않고, confirm 단계에서 실제 객체 Content-Type을 다시 검증한다.
	 */
	public static String canonicalMimeType(String mimeType) {
		if (mimeType == null) {
			return null;
		}
		return ImageMimeType.canonicalMimeType(mimeType);
	}

	private static String canonicalConfiguredMimeType(String mimeType) {
		if (mimeType == null || mimeType.isBlank()) {
			throw new AnswerException(AnswerErrorCode.INVALID_MEDIA_METADATA, "allowedMimeTypes",
				"qello.media.allowed-mime-types에 빈 값이 있습니다");
		}
		String canonical = canonicalMimeType(mimeType);
		if (canonical == null) {
			throw new AnswerException(AnswerErrorCode.INVALID_MEDIA_METADATA, "allowedMimeTypes",
				"qello.media.allowed-mime-types는 JPEG/PNG만 지원합니다");
		}
		return canonical;
	}
}
