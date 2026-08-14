package com.dnd.qello.answer.service;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import com.dnd.qello.answer.config.MediaStorageProperties;
import com.dnd.qello.answer.domain.MediaAsset;
import com.dnd.qello.answer.domain.MediaAssetStatus;
import com.dnd.qello.answer.domain.ImageMimeType;
import com.dnd.qello.answer.error.AnswerErrorCode;
import com.dnd.qello.answer.error.AnswerException;
import com.dnd.qello.answer.repository.MediaAssetRepository;
import com.dnd.qello.answer.service.port.ObjectStoragePort;
import com.dnd.qello.answer.service.port.PresignedUpload;
import com.dnd.qello.answer.service.port.StoredObjectMetadata;

import lombok.RequiredArgsConstructor;

/** presigned URL 발급과 업로드 완료 확인(confirm)을 담당한다. */
@Service
@RequiredArgsConstructor
public class MediaUploadService {

	private final MediaAssetRepository mediaAssetRepository;
	private final ObjectStoragePort objectStoragePort;
	private final MediaStorageProperties properties;
	private final MediaAssetStatusTransitionService statusTransitionService;

	@Transactional
	public UploadUrl issueUploadUrl(IssueUploadUrlCommand command) {
		if (command.requesterId() != command.ownerId()) {
			throw new AnswerException(
				AnswerErrorCode.MEDIA_OWNER_MISMATCH, "ownerId", "본인 명의로만 업로드를 요청할 수 있습니다");
		}
		String canonicalMimeType = ImageMimeType.canonicalMimeType(command.mimeType());
		if (!properties.isAllowedMimeType(canonicalMimeType)) {
			throw new AnswerException(
				AnswerErrorCode.INVALID_MEDIA_METADATA, "mimeType", "허용되지 않는 mime type입니다");
		}
		if (command.byteSize() > properties.maxByteSize()) {
			throw new AnswerException(
				AnswerErrorCode.INVALID_MEDIA_METADATA, "byteSize", "허용된 최대 크기를 초과했습니다");
		}

		String storageKey = "media/" + command.ownerId() + "/" + UUID.randomUUID();
		MediaAsset saved = mediaAssetRepository.save(MediaAsset.upload(command.ownerId(), storageKey,
			canonicalMimeType, command.byteSize(), command.checksum(), command.requestedAt()));
		PresignedUpload presigned = objectStoragePort.issuePutUrl(storageKey, canonicalMimeType, properties.uploadUrlTtl());
		return new UploadUrl(saved, presigned);
	}

	/**
	 * UPLOADING이 아니면 이미 확정된 결과를 그대로 반환한다(멱등) — 재호출로 HeadObject를
	 * 다시 부르거나 상태를 다시 확정하지 않는다. 외부 S3 I/O 동안 호출자 트랜잭션을
	 * 중단하고, transitionFromUploading의 조건부 UPDATE만 별도 짧은 트랜잭션으로 실행한다.
	 * transitionFromUploading이 경쟁에서 진 경우(0행)도 현재 상태를 다시 읽어 멱등하게 반환한다.
	 */
	@Transactional(propagation = Propagation.NOT_SUPPORTED)
	public MediaAsset confirm(long mediaId, long requesterId) {
		if (mediaId <= 0) {
			throw new AnswerException(AnswerErrorCode.INVALID_ID, "mediaId", "미디어 식별자가 올바르지 않습니다");
		}
		if (requesterId <= 0) {
			throw new AnswerException(AnswerErrorCode.INVALID_ID, "requesterId", "요청자 식별자가 올바르지 않습니다");
		}
		MediaAsset asset = mediaAssetRepository.findByIdAndOwnerId(mediaId, requesterId)
			.orElseThrow(() -> new AnswerException(AnswerErrorCode.MEDIA_NOT_FOUND, "mediaId", "미디어를 찾을 수 없습니다"));
		if (asset.getStatus() != MediaAssetStatus.UPLOADING) {
			return asset;
		}

		Optional<StoredObjectMetadata> metadata = objectStoragePort.headObject(asset.getStorageKey());
		MediaAsset resolved = metadata.filter(found -> matches(asset, found))
			.map(found -> asset.ready())
			.orElseGet(asset::reject);

		return statusTransitionService.transitionFromUploading(resolved)
			.orElseGet(() -> mediaAssetRepository.findByIdAndOwnerId(mediaId, requesterId)
				.orElseThrow(() -> new AnswerException(AnswerErrorCode.MEDIA_NOT_FOUND, "mediaId", "미디어를 찾을 수 없습니다")));
	}

	private static boolean matches(MediaAsset asset, StoredObjectMetadata metadata) {
		String contentType = ImageMimeType.canonicalMimeType(metadata.contentType());
		return metadata.contentLength() == asset.getByteSize() && asset.getMimeType().equals(contentType);
	}

	public record IssueUploadUrlCommand(
		long requesterId, long ownerId, String mimeType, long byteSize, String checksum, Instant requestedAt) {
		public IssueUploadUrlCommand {
			if (requesterId <= 0 || ownerId <= 0) {
				throw new AnswerException(AnswerErrorCode.INVALID_ID, null, "ID가 유효하지 않습니다");
			}
			if (mimeType == null || mimeType.isBlank() || checksum == null || checksum.isBlank()) {
				throw new AnswerException(AnswerErrorCode.REQUIRED_VALUE_MISSING, null, "필수 command 값이 없습니다");
			}
			if (byteSize <= 0) {
				throw new AnswerException(AnswerErrorCode.INVALID_MEDIA_METADATA, "byteSize", "byteSize는 양수여야 합니다");
			}
			if (requestedAt == null) {
				throw new AnswerException(AnswerErrorCode.REQUIRED_VALUE_MISSING, "requestedAt", "requestedAt은 필수입니다");
			}
		}
	}

	public record UploadUrl(MediaAsset asset, PresignedUpload presignedUpload) {
	}
}
