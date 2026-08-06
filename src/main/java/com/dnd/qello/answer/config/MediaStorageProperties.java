package com.dnd.qello.answer.config;

import java.time.Duration;
import java.util.Set;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * presigned URL 발급 정책. 실제 버킷 이름과 리전은 환경별 값이므로 여기 기본값을 두지
 * 않고 프로필별 properties에서 주입한다(local: 실제 dev 버킷, test: LocalStack 버킷).
 */
@ConfigurationProperties(prefix = "qello.media")
public record MediaStorageProperties(String bucket, Set<String> allowedMimeTypes, long maxByteSize,
	Duration uploadUrlTtl) {

	public MediaStorageProperties {
		if (bucket == null || bucket.isBlank()) {
			throw new IllegalArgumentException("qello.media.bucket은 필수입니다");
		}
		if (allowedMimeTypes == null || allowedMimeTypes.isEmpty()) {
			throw new IllegalArgumentException("qello.media.allowed-mime-types는 최소 1개 이상이어야 합니다");
		}
		if (maxByteSize <= 0) {
			throw new IllegalArgumentException("qello.media.max-byte-size는 양수여야 합니다");
		}
		if (uploadUrlTtl == null || uploadUrlTtl.isZero() || uploadUrlTtl.isNegative()) {
			throw new IllegalArgumentException("qello.media.upload-url-ttl은 양수여야 합니다");
		}
	}

	public boolean isAllowedMimeType(String mimeType) {
		return mimeType != null && allowedMimeTypes.contains(mimeType);
	}
}
