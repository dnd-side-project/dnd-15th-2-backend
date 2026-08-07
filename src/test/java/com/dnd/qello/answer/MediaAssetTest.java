package com.dnd.qello.answer;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Instant;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.dnd.qello.answer.domain.MediaAsset;
import com.dnd.qello.answer.domain.MediaAssetStatus;
import com.dnd.qello.answer.error.AnswerErrorCode;
import com.dnd.qello.answer.error.AnswerException;

/**
 * Created at: 2026-08-07T02:40:00+09:00
 * Source scenario: TEST-PLAN-GH-70-MEDIA-ASSET-SERVICE-UNIT-001 through UNIT-003
 */
class MediaAssetTest {

	private static final Instant CREATED = Instant.parse("2026-08-07T00:00:00Z");

	@Test
	@DisplayName("MediaAsset은 소유자·storageKey·mimeType·byteSize·checksum을 구조적으로 검증한다")
	void validatesStructuralMetadata() {
		MediaAsset asset = MediaAsset.upload(10L, "answer/10/uuid.jpg", "image/jpeg", 1024L, "checksum", CREATED);

		assertThat(asset.getId()).isNull();
		assertThat(asset.getOwnerId()).isEqualTo(10L);
		assertThat(asset.getStatus()).isEqualTo(MediaAssetStatus.UPLOADING);

		assertThatThrownBy(() -> MediaAsset.upload(0L, "key", "image/jpeg", 1024L, "checksum", CREATED))
			.isInstanceOf(AnswerException.class)
			.hasFieldOrPropertyWithValue("errorCode", AnswerErrorCode.INVALID_ID);
		assertThatThrownBy(() -> MediaAsset.upload(10L, "  ", "image/jpeg", 1024L, "checksum", CREATED))
			.isInstanceOf(AnswerException.class)
			.hasFieldOrPropertyWithValue("errorCode", AnswerErrorCode.INVALID_MEDIA_METADATA);
		assertThatThrownBy(() -> MediaAsset.upload(10L, "key", "image/jpeg", 0L, "checksum", CREATED))
			.isInstanceOf(AnswerException.class)
			.hasFieldOrPropertyWithValue("errorCode", AnswerErrorCode.INVALID_MEDIA_METADATA);
	}

	@Test
	@DisplayName("MediaAsset은 UPLOADING에서만 READY 또는 REJECTED로 전이한다")
	void transitionsOnlyFromUploading() {
		MediaAsset uploading = MediaAsset.upload(10L, "key", "image/jpeg", 1024L, "checksum", CREATED);

		MediaAsset ready = uploading.ready();
		assertThat(ready.getStatus()).isEqualTo(MediaAssetStatus.READY);
		assertThatThrownBy(ready::ready)
			.isInstanceOf(AnswerException.class)
			.hasFieldOrPropertyWithValue("errorCode", AnswerErrorCode.INVALID_MEDIA_STATUS);
		assertThatThrownBy(ready::reject)
			.isInstanceOf(AnswerException.class)
			.hasFieldOrPropertyWithValue("errorCode", AnswerErrorCode.INVALID_MEDIA_STATUS);

		MediaAsset rejected = MediaAsset.upload(10L, "key2", "image/jpeg", 1024L, "checksum", CREATED).reject();
		assertThat(rejected.getStatus()).isEqualTo(MediaAssetStatus.REJECTED);
	}

	@Test
	@DisplayName("DELETED MediaAsset은 deletedAt을 반드시 가지며 어떤 상태로도 다시 전이할 수 없다")
	void deletedIsTerminal() {
		MediaAsset asset = MediaAsset.upload(10L, "key", "image/jpeg", 1024L, "checksum", CREATED);
		MediaAsset deleted = asset.delete(CREATED.plusSeconds(1));

		assertThat(deleted.getStatus()).isEqualTo(MediaAssetStatus.DELETED);
		assertThat(deleted.getDeletedAt()).isEqualTo(CREATED.plusSeconds(1));
		assertThatThrownBy(() -> deleted.delete(CREATED.plusSeconds(2)))
			.isInstanceOf(AnswerException.class)
			.hasFieldOrPropertyWithValue("errorCode", AnswerErrorCode.INVALID_MEDIA_STATUS);
		assertThatThrownBy(deleted::ready)
			.isInstanceOf(AnswerException.class)
			.hasFieldOrPropertyWithValue("errorCode", AnswerErrorCode.INVALID_MEDIA_STATUS);

		assertThatThrownBy(() -> MediaAsset.restore(1L, 10L, MediaAssetStatus.DELETED, "key", "image/jpeg", 1024L,
			"checksum", CREATED, null))
			.isInstanceOf(AnswerException.class)
			.hasFieldOrPropertyWithValue("errorCode", AnswerErrorCode.INVALID_MEDIA_STATE);
	}
}
