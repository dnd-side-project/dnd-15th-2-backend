/**
 * Created at: 2026-08-14T16:00:00+09:00
 * Source scenario: TEST-PLAN-GH-122-DIRECTION-PREVIEW-SUBMISSION-API-UNIT-004
 */
package com.dnd.qello.answer;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.net.MalformedURLException;
import java.net.URI;
import java.net.URL;
import java.time.Duration;
import java.time.Instant;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.dnd.qello.answer.config.MediaStorageProperties;
import com.dnd.qello.answer.domain.MediaAsset;
import com.dnd.qello.answer.domain.MediaAssetStatus;
import com.dnd.qello.answer.domain.ImageMimeType;
import com.dnd.qello.answer.error.AnswerErrorCode;
import com.dnd.qello.answer.error.AnswerException;
import com.dnd.qello.answer.repository.MediaAssetRepository;
import com.dnd.qello.answer.service.MediaAssetStatusTransitionService;
import com.dnd.qello.answer.service.MediaUploadService;
import com.dnd.qello.answer.service.MediaUploadService.IssueUploadUrlCommand;
import com.dnd.qello.answer.service.MediaUploadService.UploadUrl;
import com.dnd.qello.answer.service.port.ObjectStoragePort;
import com.dnd.qello.answer.service.port.PresignedUpload;
import com.dnd.qello.answer.service.port.StoredObjectMetadata;

class MediaUploadServiceTest {

	private static final Instant NOW = Instant.parse("2026-08-07T00:00:00Z");

	private final InMemoryMediaAssetRepository repository = new InMemoryMediaAssetRepository();
	private final FakeObjectStoragePort storage = new FakeObjectStoragePort();
	private final MediaStorageProperties properties = new MediaStorageProperties(
		"test-bucket", ImageMimeType.supportedMimeTypes(), 1_000L, Duration.ofMinutes(10));
	private final MediaAssetStatusTransitionService statusTransitionService =
		new MediaAssetStatusTransitionService(repository);
	private final MediaUploadService service =
		new MediaUploadService(repository, storage, properties, statusTransitionService);

	@Test
	@DisplayName("발급 요청자와 소유자가 다르면 presigned URL을 발급하지 않는다")
	void rejectsIssueWhenRequesterIsNotOwner() {
		assertThatThrownBy(() -> service.issueUploadUrl(
			new IssueUploadUrlCommand(1L, 2L, "image/jpeg", 500L, "checksum", NOW)))
			.isInstanceOf(AnswerException.class)
			.hasFieldOrPropertyWithValue("errorCode", AnswerErrorCode.MEDIA_OWNER_MISMATCH);
	}

	@Test
	@DisplayName("화이트리스트 밖의 mime type이나 허용 크기를 넘는 요청은 거부된다")
	void rejectsDisallowedMimeAndOversizedRequests() {
		assertThatThrownBy(() -> service.issueUploadUrl(
			new IssueUploadUrlCommand(1L, 1L, "image/gif", 500L, "checksum", NOW)))
			.isInstanceOf(AnswerException.class)
			.hasFieldOrPropertyWithValue("errorCode", AnswerErrorCode.INVALID_MEDIA_METADATA);
		assertThatThrownBy(() -> service.issueUploadUrl(
			new IssueUploadUrlCommand(1L, 1L, "image/jpeg", 10_000L, "checksum", NOW)))
			.isInstanceOf(AnswerException.class)
			.hasFieldOrPropertyWithValue("errorCode", AnswerErrorCode.INVALID_MEDIA_METADATA);
	}

	@Test
	@DisplayName("JPG MIME 별칭은 image/jpeg로 정규화하고 WebP는 허용하지 않는다")
	void canonicalizesJpgAliasAndRejectsWebp() {
		UploadUrl result = service.issueUploadUrl(
			new IssueUploadUrlCommand(1L, 1L, " IMAGE/JPG ", 500L, "checksum", NOW));

		assertThat(result.asset().getMimeType()).isEqualTo("image/jpeg");
		assertThat(result.presignedUpload()).isNotNull();
		assertThatThrownBy(() -> service.issueUploadUrl(
			new IssueUploadUrlCommand(1L, 1L, "image/webp", 500L, "checksum", NOW)))
			.isInstanceOf(AnswerException.class)
			.hasFieldOrPropertyWithValue("errorCode", AnswerErrorCode.INVALID_MEDIA_METADATA);
	}

	@Test
	@DisplayName("허용 MIME은 포맷 타입의 canonical 값으로 저장한다")
	void storesCanonicalMimeTypeFromImageFormat() {
		UploadUrl result = service.issueUploadUrl(
			new IssueUploadUrlCommand(1L, 1L, " IMAGE/JPEG ", 500L, "checksum", NOW));

		assertThat(result.asset().getMimeType()).isEqualTo(ImageMimeType.JPEG.mimeType());
	}

	@Test
	@DisplayName("JPEG·PNG 이외 형식을 허용하는 미디어 설정은 애플리케이션 시작 전에 거부된다")
	void rejectsUnsupportedMimeConfiguration() {
		assertThatThrownBy(() -> new MediaStorageProperties(
			"test-bucket", Set.of("image/jpeg", "image/webp"), 1_000L, Duration.ofMinutes(10)))
			.isInstanceOf(AnswerException.class)
			.hasFieldOrPropertyWithValue("errorCode", AnswerErrorCode.INVALID_MEDIA_METADATA)
			.hasFieldOrPropertyWithValue("reason", "qello.media.allowed-mime-types는 JPEG/PNG만 지원합니다");
	}

	@Test
	@DisplayName("JPEG만 허용하는 부분 화이트리스트 설정은 PNG 누락으로 시작 전에 거부된다")
	void rejectsPartialMimeConfiguration() {
		assertThatThrownBy(() -> new MediaStorageProperties(
			"test-bucket", Set.of("image/jpeg"), 1_000L, Duration.ofMinutes(10)))
			.isInstanceOf(AnswerException.class)
			.hasFieldOrPropertyWithValue("errorCode", AnswerErrorCode.INVALID_MEDIA_METADATA);
	}

	@Test
	@DisplayName("소유자 본인이 허용 범위 안에서 요청하면 UPLOADING 자산과 presigned URL을 함께 반환한다")
	void issuesUrlForOwnerWithinWhitelist() {
		UploadUrl result = service.issueUploadUrl(
			new IssueUploadUrlCommand(1L, 1L, "image/jpeg", 500L, "checksum", NOW));

		assertThat(result.asset().getStatus()).isEqualTo(MediaAssetStatus.UPLOADING);
		assertThat(result.presignedUpload().url()).isNotNull();
	}

	@Test
	@DisplayName("PNG도 허용 목록에서 presigned URL을 발급한다")
	void issuesUrlForPng() {
		UploadUrl result = service.issueUploadUrl(
			new IssueUploadUrlCommand(1L, 1L, "image/png", 1_000L, "checksum", NOW));

		assertThat(result.asset().getMimeType()).isEqualTo("image/png");
		assertThat(result.presignedUpload().url()).isNotNull();
	}

	@Test
	@DisplayName("확인 시점에 크기·타입이 일치하는 객체가 있으면 READY로 전이한다")
	void confirmsReadyWhenObjectMatches() {
		MediaAsset asset = repository.save(MediaAsset.upload(1L, "media/1/key", "image/jpeg", 500L, "checksum", NOW));
		storage.put("media/1/key", 500L, "image/jpeg");

		MediaAsset confirmed = service.confirm(asset.getId(), 1L);

		assertThat(confirmed.getStatus()).isEqualTo(MediaAssetStatus.READY);
	}

	@Test
	@DisplayName("MIME과 크기가 일치해도 JPEG 시그니처가 아니면 REJECTED로 전이한다")
	void rejectsNonImageBytesWithMatchingMetadata() {
		MediaAsset asset = repository.save(MediaAsset.upload(1L, "media/1/not-image", "image/jpeg", 500L, "checksum", NOW));
		storage.putRaw("media/1/not-image", 500L, "image/jpeg", new byte[] {0x00, 0x01, 0x02});

		MediaAsset confirmed = service.confirm(asset.getId(), 1L);

		assertThat(confirmed.getStatus()).isEqualTo(MediaAssetStatus.REJECTED);
	}

	@Test
	@DisplayName("confirm은 저장소의 대소문자·공백이 있는 JPG MIME도 JPEG로 정규화해 READY 처리한다")
	void confirmsReadyForNormalizedJpgMetadata() {
		MediaAsset asset = repository.save(MediaAsset.upload(1L, "media/1/jpg", "image/jpeg", 500L, "checksum", NOW));
		storage.put("media/1/jpg", 500L, " IMAGE/JPG ");

		MediaAsset confirmed = service.confirm(asset.getId(), 1L);

		assertThat(confirmed.getStatus()).isEqualTo(MediaAssetStatus.READY);
	}

	@Test
	@DisplayName("객체가 없거나 크기·타입이 다르면 REJECTED로 전이한다")
	void rejectsWhenObjectMissingOrMismatched() {
		MediaAsset missing = repository.save(MediaAsset.upload(1L, "media/1/missing", "image/jpeg", 500L, "checksum", NOW));
		MediaAsset mismatched = repository.save(MediaAsset.upload(1L, "media/1/mismatched", "image/jpeg", 500L, "checksum", NOW));
		storage.put("media/1/mismatched", 999L, "image/jpeg");

		assertThat(service.confirm(missing.getId(), 1L).getStatus()).isEqualTo(MediaAssetStatus.REJECTED);
		assertThat(service.confirm(mismatched.getId(), 1L).getStatus()).isEqualTo(MediaAssetStatus.REJECTED);
	}

	@Test
	@DisplayName("이미 확정된 미디어를 다시 confirm하면 저장소를 다시 조회하지 않고 같은 결과를 멱등하게 반환한다")
	void confirmIsIdempotentAfterResolution() {
		MediaAsset asset = repository.save(MediaAsset.upload(1L, "media/1/key", "image/jpeg", 500L, "checksum", NOW));
		storage.put("media/1/key", 500L, "image/jpeg");

		MediaAsset first = service.confirm(asset.getId(), 1L);
		storage.remove("media/1/key");
		MediaAsset second = service.confirm(asset.getId(), 1L);

		assertThat(first.getStatus()).isEqualTo(MediaAssetStatus.READY);
		assertThat(second.getStatus()).isEqualTo(MediaAssetStatus.READY);
	}

	private static final class FakeObjectStoragePort implements ObjectStoragePort {
		private final Map<String, StoredObjectMetadata> objects = new HashMap<>();
		private final Map<String, byte[]> prefixes = new HashMap<>();

		void put(String key, long size, String contentType) {
			putRaw(key, size, contentType, signature(contentType));
		}

		void putRaw(String key, long size, String contentType, byte[] prefix) {
			objects.put(key, new StoredObjectMetadata(size, contentType));
			prefixes.put(key, prefix.clone());
		}

		void remove(String key) {
			objects.remove(key);
			prefixes.remove(key);
		}

		@Override
		public PresignedUpload issuePutUrl(String storageKey, String contentType, Duration ttl) {
			try {
				URL url = URI.create("https://example-test.invalid/" + storageKey).toURL();
				return new PresignedUpload(url, Instant.now().plus(ttl));
			} catch (MalformedURLException exception) {
				throw new IllegalStateException(exception);
			}
		}

		@Override
		public Optional<StoredObjectMetadata> headObject(String storageKey) {
			return Optional.ofNullable(objects.get(storageKey));
		}

		@Override
		public Optional<byte[]> readObjectPrefix(String storageKey, int maxBytes) {
			return Optional.ofNullable(prefixes.get(storageKey))
				.map(prefix -> Arrays.copyOf(prefix, Math.min(prefix.length, maxBytes)));
		}

		private static byte[] signature(String contentType) {
			return ImageMimeType.fromMimeType(contentType)
				.map(format -> format == ImageMimeType.PNG
					? new byte[] {(byte) 0x89, 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A}
					: new byte[] {(byte) 0xFF, (byte) 0xD8, (byte) 0xFF})
				.orElse(new byte[] {0x00});
		}
	}

	private static final class InMemoryMediaAssetRepository implements MediaAssetRepository {
		private final Map<Long, MediaAsset> store = new HashMap<>();
		private long nextId = 1;

		@Override
		public MediaAsset save(MediaAsset asset) {
			long id = asset.getId() != null ? asset.getId() : nextId++;
			MediaAsset persisted = MediaAsset.restore(id, asset.getOwnerId(), asset.getStatus(), asset.getStorageKey(),
				asset.getMimeType(), asset.getByteSize(), asset.getChecksum(), asset.getCreatedAt(), asset.getDeletedAt());
			store.put(id, persisted);
			return persisted;
		}

		@Override
		public Optional<MediaAsset> findById(long id) {
			return Optional.ofNullable(store.get(id));
		}

		@Override
		public Optional<MediaAsset> findByIdAndOwnerId(long id, long ownerId) {
			return findById(id).filter(asset -> asset.getOwnerId() == ownerId);
		}

		@Override
		public Optional<MediaAsset> transitionFromUploading(MediaAsset next) {
			MediaAsset current = store.get(next.getId());
			if (current == null || current.getStatus() != MediaAssetStatus.UPLOADING) {
				return Optional.empty();
			}
			store.put(next.getId(), next);
			return Optional.of(next);
		}
	}
}
