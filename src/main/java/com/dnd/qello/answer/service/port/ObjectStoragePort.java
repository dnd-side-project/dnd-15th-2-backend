package com.dnd.qello.answer.service.port;

import java.time.Duration;
import java.util.Optional;

/** presigned URL 발급과 업로드 확인에 필요한 최소 S3 연산. */
public interface ObjectStoragePort {

	PresignedUpload issuePutUrl(String storageKey, String contentType, Duration ttl);

	/** 객체가 없으면(HeadObject 404) empty를 반환한다 — 예외가 아니라 정상적인 미확인 상태다. */
	Optional<StoredObjectMetadata> headObject(String storageKey);

	/**
	 * 객체 앞부분을 읽는다. 객체가 없으면 empty를 반환하고, 외부 저장소 장애는
	 * {@code STORAGE_UNAVAILABLE}으로 변환한다.
	 */
	Optional<byte[]> readObjectPrefix(String storageKey, int maxBytes);
}
