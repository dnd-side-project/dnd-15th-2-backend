package com.dnd.qello.answer.domain;

import java.time.Instant;

import com.dnd.qello.answer.error.AnswerErrorCode;
import com.dnd.qello.answer.error.AnswerException;

/**
 * S3에 올라간 원본 파일 하나를 표현한다. mime/size 화이트리스트와 presigned URL 발급 정책은
 * 설정값을 아는 서비스 계층이 검증하고, 이 클래스는 구조적 불변식만 강제한다.
 */
public final class MediaAsset {

	private static final int STORAGE_KEY_MAX_LENGTH = 500;
	private static final int MIME_TYPE_MAX_LENGTH = 100;
	private static final int CHECKSUM_MAX_LENGTH = 128;

	private final Long id;
	private final long ownerId;
	private final MediaAssetStatus status;
	private final String storageKey;
	private final String mimeType;
	private final long byteSize;
	private final String checksum;
	private final Instant createdAt;
	private final Instant deletedAt;

	private MediaAsset(Long id, long ownerId, MediaAssetStatus status, String storageKey, String mimeType,
		long byteSize, String checksum, Instant createdAt, Instant deletedAt) {
		this.id = validateIdOrNull(id, "id");
		this.ownerId = requireId(ownerId, "ownerId");
		this.status = requireValue(status, "status");
		this.storageKey = requiredText(storageKey, "storageKey", STORAGE_KEY_MAX_LENGTH);
		this.mimeType = requiredText(mimeType, "mimeType", MIME_TYPE_MAX_LENGTH);
		this.byteSize = requirePositiveSize(byteSize, "byteSize");
		this.checksum = requiredText(checksum, "checksum", CHECKSUM_MAX_LENGTH);
		this.createdAt = requireValue(createdAt, "createdAt");
		this.deletedAt = deletedAt;
		validateState();
	}

	public static MediaAsset upload(long ownerId, String storageKey, String mimeType, long byteSize,
		String checksum, Instant createdAt) {
		return new MediaAsset(null, ownerId, MediaAssetStatus.UPLOADING, storageKey, mimeType, byteSize, checksum,
			createdAt, null);
	}

	public static MediaAsset restore(Long id, long ownerId, MediaAssetStatus status, String storageKey,
		String mimeType, long byteSize, String checksum, Instant createdAt, Instant deletedAt) {
		return new MediaAsset(id, ownerId, status, storageKey, mimeType, byteSize, checksum, createdAt, deletedAt);
	}

	/** 업로드된 객체가 검증을 통과했을 때만 호출한다. UPLOADING에서만 전이할 수 있다. */
	public MediaAsset ready() {
		requireStatus(MediaAssetStatus.UPLOADING, "READY로 전환");
		return copy(MediaAssetStatus.READY, deletedAt);
	}

	/** 업로드 확인(HeadObject)이 실패했을 때 호출한다. UPLOADING에서만 전이할 수 있다. */
	public MediaAsset reject() {
		requireStatus(MediaAssetStatus.UPLOADING, "REJECTED로 전환");
		return copy(MediaAssetStatus.REJECTED, deletedAt);
	}

	/** UPLOADING/READY/REJECTED에서만 삭제할 수 있다. DELETED는 terminal 상태다. */
	public MediaAsset delete(Instant at) {
		if (status == MediaAssetStatus.DELETED) {
			throw new AnswerException(
				AnswerErrorCode.INVALID_MEDIA_STATUS, "status", "이미 삭제된 미디어입니다");
		}
		requireValue(at, "deletedAt");
		return copy(MediaAssetStatus.DELETED, at);
	}

	private MediaAsset copy(MediaAssetStatus nextStatus, Instant nextDeletedAt) {
		return new MediaAsset(id, ownerId, nextStatus, storageKey, mimeType, byteSize, checksum, createdAt,
			nextDeletedAt);
	}

	private void requireStatus(MediaAssetStatus expected, String action) {
		if (status != expected) {
			throw new AnswerException(
				AnswerErrorCode.INVALID_MEDIA_STATUS, "status", status + " 상태에서는 " + action + "할 수 없습니다");
		}
	}

	private void validateState() {
		if ((status == MediaAssetStatus.DELETED) != (deletedAt != null)) {
			throw new AnswerException(
				AnswerErrorCode.INVALID_MEDIA_STATE, "deletedAt", "DELETED 상태와 deletedAt은 함께 설정되어야 합니다");
		}
	}

	private static <T> T requireValue(T value, String field) {
		if (value == null) {
			throw new AnswerException(AnswerErrorCode.REQUIRED_VALUE_MISSING, field, field + "은 필수입니다");
		}
		return value;
	}

	private static String requiredText(String value, String field, int maxLength) {
		if (value == null || value.isBlank() || value.length() > maxLength) {
			throw new AnswerException(AnswerErrorCode.INVALID_MEDIA_METADATA, field, field + " 값이 유효하지 않습니다");
		}
		return value;
	}

	private static Long validateIdOrNull(Long value, String field) {
		return value == null ? null : requireId(value, field);
	}

	private static long requireId(long value, String field) {
		if (value <= 0) {
			throw new AnswerException(AnswerErrorCode.INVALID_ID, field, field + "는 양수여야 합니다");
		}
		return value;
	}

	private static long requirePositiveSize(long value, String field) {
		if (value <= 0) {
			throw new AnswerException(AnswerErrorCode.INVALID_MEDIA_METADATA, field, field + "는 양수여야 합니다");
		}
		return value;
	}

	public Long getId() { return id; }
	public long getOwnerId() { return ownerId; }
	public MediaAssetStatus getStatus() { return status; }
	public String getStorageKey() { return storageKey; }
	public String getMimeType() { return mimeType; }
	public long getByteSize() { return byteSize; }
	public String getChecksum() { return checksum; }
	public Instant getCreatedAt() { return createdAt; }
	public Instant getDeletedAt() { return deletedAt; }
}
