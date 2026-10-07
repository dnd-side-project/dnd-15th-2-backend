/**
 * Created at: 2026-08-14T16:00:00+09:00
 * Source scenario: TEST-PLAN-GH-122-DIRECTION-PREVIEW-SUBMISSION-API-UNIT-004,
 * TEST-PLAN-GH-325-EXIF-STRIP-UNIT-016 through UNIT-022 (added 2026-10-07T23:23:00+09:00)
 */
package com.dnd.qello.answer;

import java.net.MalformedURLException;
import java.net.URI;
import java.net.URL;
import java.nio.ByteOrder;
import java.time.Duration;
import java.time.Instant;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.dnd.qello.answer.config.MediaStorageProperties;
import com.dnd.qello.answer.domain.ImageMimeType;
import com.dnd.qello.answer.domain.MediaAsset;
import com.dnd.qello.answer.domain.MediaAssetStatus;
import com.dnd.qello.answer.domain.MediaStorageKeys;
import com.dnd.qello.answer.error.AnswerErrorCode;
import com.dnd.qello.answer.error.AnswerException;
import com.dnd.qello.answer.repository.MediaAssetRepository;
import com.dnd.qello.answer.service.MediaAssetStatusTransitionService;
import com.dnd.qello.answer.service.MediaUploadService;
import com.dnd.qello.answer.service.MediaUploadService.IssueUploadUrlCommand;
import com.dnd.qello.answer.service.MediaUploadService.UploadUrl;
import com.dnd.qello.answer.service.port.ObjectStoragePort;
import com.dnd.qello.answer.service.port.PresignedUpload;
import com.dnd.qello.answer.service.port.PresignedView;
import com.dnd.qello.answer.service.port.StoredObjectMetadata;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.entry;

class MediaUploadServiceTest {

	private static final Instant NOW = Instant.parse("2026-08-07T00:00:00Z");

	private final InMemoryMediaAssetRepository repository = new InMemoryMediaAssetRepository();
	private final FakeObjectStoragePort storage = new FakeObjectStoragePort();
	private final MediaStorageProperties properties = new MediaStorageProperties(
			"test-bucket", ImageMimeType.supportedMimeTypes(), 1_000L, Duration.ofMinutes(10),
			Duration.ofMinutes(5), "media/defaults/profile-image.png");
	private final MediaAssetStatusTransitionService statusTransitionService = new MediaAssetStatusTransitionService(
			repository);
	private final MediaUploadService service = new MediaUploadService(repository, storage, properties,
			statusTransitionService);

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
				"test-bucket", Set.of("image/jpeg", "image/webp"), 1_000L, Duration.ofMinutes(10),
				Duration.ofMinutes(5), "media/defaults/profile-image.png"))
				.isInstanceOf(AnswerException.class)
				.hasFieldOrPropertyWithValue("errorCode", AnswerErrorCode.INVALID_MEDIA_METADATA)
				.hasFieldOrPropertyWithValue("reason", "qello.media.allowed-mime-types는 JPEG/PNG만 지원합니다");
	}

	@Test
	@DisplayName("JPEG만 허용하는 부분 화이트리스트 설정은 PNG 누락으로 시작 전에 거부된다")
	void rejectsPartialMimeConfiguration() {
		assertThatThrownBy(() -> new MediaStorageProperties(
				"test-bucket", Set.of("image/jpeg"), 1_000L, Duration.ofMinutes(10),
				Duration.ofMinutes(5), "media/defaults/profile-image.png"))
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
		byte[] jpeg = ExifTestImages.baseJpeg();
		MediaAsset asset = repository.save(uploading("uploads/1/key", jpeg.length));
		storage.put("uploads/1/key", "image/jpeg", jpeg);

		MediaAsset confirmed = service.confirm(asset.getId(), 1L);

		assertThat(confirmed.getStatus()).isEqualTo(MediaAssetStatus.READY);
	}

	@Test
	@DisplayName("MIME과 크기가 일치해도 JPEG 시그니처가 아니면 REJECTED로 전이한다")
	void rejectsNonImageBytesWithMatchingMetadata() {
		MediaAsset asset = repository
				.save(MediaAsset.upload(1L, "media/1/not-image", "image/jpeg", 500L, "checksum", NOW));
		storage.putRaw("media/1/not-image", 500L, "image/jpeg", new byte[500]);

		MediaAsset confirmed = service.confirm(asset.getId(), 1L);

		assertThat(confirmed.getStatus()).isEqualTo(MediaAssetStatus.REJECTED);
	}

	@Test
	@DisplayName("confirm은 저장소의 대소문자·공백이 있는 JPG MIME도 JPEG로 정규화해 READY 처리한다")
	void confirmsReadyForNormalizedJpgMetadata() {
		byte[] jpeg = ExifTestImages.baseJpeg();
		MediaAsset asset = repository.save(uploading("uploads/1/jpg", jpeg.length));
		storage.put("uploads/1/jpg", " IMAGE/JPG ", jpeg);

		MediaAsset confirmed = service.confirm(asset.getId(), 1L);

		assertThat(confirmed.getStatus()).isEqualTo(MediaAssetStatus.READY);
	}

	@Test
	@DisplayName("객체가 없거나 크기·타입이 다르면 REJECTED로 전이한다")
	void rejectsWhenObjectMissingOrMismatched() {
		MediaAsset missing = repository
				.save(MediaAsset.upload(1L, "media/1/missing", "image/jpeg", 500L, "checksum", NOW));
		MediaAsset mismatched = repository
				.save(MediaAsset.upload(1L, "media/1/mismatched", "image/jpeg", 500L, "checksum", NOW));
		storage.putRaw("media/1/mismatched", 999L, "image/jpeg", new byte[999]);

		assertThat(service.confirm(missing.getId(), 1L).getStatus()).isEqualTo(MediaAssetStatus.REJECTED);
		assertThat(service.confirm(mismatched.getId(), 1L).getStatus()).isEqualTo(MediaAssetStatus.REJECTED);
	}

	@Test
	@DisplayName("이미 확정된 미디어를 다시 confirm하면 저장소를 다시 조회하지 않고 같은 결과를 멱등하게 반환한다")
	void confirmIsIdempotentAfterResolution() {
		byte[] jpeg = ExifTestImages.baseJpeg();
		MediaAsset asset = repository.save(uploading("uploads/1/key", jpeg.length));
		storage.put("uploads/1/key", "image/jpeg", jpeg);

		MediaAsset first = service.confirm(asset.getId(), 1L);
		storage.remove("uploads/1/key");
		int callsAfterFirst = storage.calls();
		MediaAsset second = service.confirm(asset.getId(), 1L);

		assertThat(first.getStatus()).isEqualTo(MediaAssetStatus.READY);
		assertThat(second.getStatus()).isEqualTo(MediaAssetStatus.READY);
		assertThat(second.getStorageKey()).isEqualTo(first.getStorageKey());
		assertThat(storage.calls()).as("head·read·put 호출 수").isEqualTo(callsAfterFirst);
	}

	@Test
	@DisplayName("confirm은 GPS를 지운 처리본을 serving key에 저장하고 그 key로 READY 전이하며 원본은 건드리지 않는다")
	void confirmStoresStrippedCopyUnderServingKey() {
		byte[] original = ExifTestImages.jpeg().exif(ExifTestImages.phoneTiff(ByteOrder.BIG_ENDIAN, 6)).build();
		MediaAsset asset = repository.save(uploading("uploads/1/photo", original.length));
		storage.put("uploads/1/photo", "image/jpeg", original);

		MediaAsset confirmed = service.confirm(asset.getId(), 1L);

		assertThat(confirmed.getStatus()).isEqualTo(MediaAssetStatus.READY);
		assertThat(confirmed.isExifStripped()).isTrue();
		assertThat(confirmed.getStorageKey()).isEqualTo("media/1/photo");
		assertThat(confirmed.getByteSize()).isEqualTo(original.length);
		assertThat(confirmed.getChecksum()).isEqualTo("checksum");
		assertThat(storage.contentTypeOf("media/1/photo")).isEqualTo("image/jpeg");
		byte[] served = storage.bodyOf("media/1/photo");
		List<ExifTestImages.JpegSegment> exif = ExifTestImages.headerSegments(served).stream()
				.filter(segment -> segment.marker() == ExifTestImages.APP1 && segment.hasId("Exif\0\0")).toList();
		assertThat(exif).hasSize(1);
		assertThat(ExifTestImages.ifd0(ExifTestImages.tiffOf(exif.get(0))).tags())
				.containsExactly(entry(ExifTestImages.TAG_ORIENTATION, 6));
		assertThat(ExifTestImages.contains(served, "TestMake")).isFalse();
		assertThat(storage.bodyOf("uploads/1/photo")).isEqualTo(original);
	}

	@Test
	@DisplayName("시그니처는 맞지만 구조가 깨진 이미지는 REJECTED이고 처리본을 저장하지 않는다")
	void rejectsMalformedImageWithoutWriting() {
		byte[] malformed = Arrays.copyOf(new byte[]{(byte) 0xFF, (byte) 0xD8, (byte) 0xFF, (byte) 0xE1, (byte) 0xFF,
				(byte) 0xFF}, 64);
		MediaAsset asset = repository.save(uploading("uploads/1/broken", malformed.length));
		storage.put("uploads/1/broken", "image/jpeg", malformed);

		MediaAsset confirmed = service.confirm(asset.getId(), 1L);

		assertThat(confirmed.getStatus()).isEqualTo(MediaAssetStatus.REJECTED);
		assertThat(confirmed.isExifStripped()).isFalse();
		assertThat(storage.putCalls).isZero();
	}

	@Test
	@DisplayName("원본 읽기나 처리본 저장이 저장소 장애로 실패하면 503을 전파하고 UPLOADING을 유지해 재시도로 READY가 된다")
	void storageFailureKeepsUploadingSoRetryCanSucceed() {
		byte[] jpeg = ExifTestImages.baseJpeg();
		MediaAsset readFails = repository.save(uploading("uploads/1/read-fails", jpeg.length));
		MediaAsset putFails = repository.save(uploading("uploads/1/put-fails", jpeg.length));
		storage.put("uploads/1/read-fails", "image/jpeg", jpeg);
		storage.put("uploads/1/put-fails", "image/jpeg", jpeg);

		storage.failRead = true;
		assertStorageUnavailable(readFails.getId());
		storage.failRead = false;
		storage.failPut = true;
		assertStorageUnavailable(putFails.getId());
		storage.failPut = false;

		assertThat(statusOf(readFails)).isEqualTo(MediaAssetStatus.UPLOADING);
		assertThat(statusOf(putFails)).isEqualTo(MediaAssetStatus.UPLOADING);
		assertThat(service.confirm(readFails.getId(), 1L).getStatus()).isEqualTo(MediaAssetStatus.READY);
		assertThat(service.confirm(putFails.getId(), 1L).getStatus()).isEqualTo(MediaAssetStatus.READY);
	}

	@Test
	@DisplayName("HeadObject 뒤에 객체가 바뀌어 읽은 길이가 신고 크기와 다르면 REJECTED이고 처리본을 저장하지 않는다")
	void rejectsWhenObjectChangedAfterHead() {
		byte[] jpeg = ExifTestImages.baseJpeg();
		MediaAsset asset = repository.save(uploading("uploads/1/swapped", jpeg.length - 1));
		storage.putRaw("uploads/1/swapped", jpeg.length - 1, "image/jpeg", jpeg);

		MediaAsset confirmed = service.confirm(asset.getId(), 1L);

		assertThat(confirmed.getStatus()).isEqualTo(MediaAssetStatus.REJECTED);
		assertThat(storage.putCalls).isZero();
	}

	@Test
	@DisplayName("serving key는 upload key와 다르고 같은 upload key에는 항상 같은 값이 나온다")
	void servingKeyDiffersFromUploadKeyAndIsDeterministic() {
		String upload = MediaStorageKeys.uploadKey(7L, UUID.fromString("00000000-0000-0000-0000-000000000001"));
		String legacy = "media/7/00000000-0000-0000-0000-000000000001";

		assertThat(upload).isEqualTo("uploads/7/00000000-0000-0000-0000-000000000001");
		assertThat(MediaStorageKeys.servingKeyOf(upload)).isEqualTo("media/7/00000000-0000-0000-0000-000000000001")
				.isEqualTo(MediaStorageKeys.servingKeyOf(upload));
		assertThat(MediaStorageKeys.servingKeyOf(legacy)).isNotEqualTo(legacy)
				.isEqualTo(MediaStorageKeys.servingKeyOf(legacy));
	}

	@Test
	@DisplayName("presigned URL 발급은 uploads/{ownerId}/ 아래의 upload key로 UPLOADING 자산을 만든다")
	void issuesUploadUrlUnderUploadsPrefix() {
		UploadUrl result = service.issueUploadUrl(
				new IssueUploadUrlCommand(1L, 1L, "image/jpeg", 500L, "checksum", NOW));

		assertThat(result.asset().getStorageKey()).startsWith("uploads/1/");
		assertThat(result.asset().isExifStripped()).isFalse();
	}

	private MediaAsset uploading(String uploadKey, long byteSize) {
		return MediaAsset.upload(1L, uploadKey, "image/jpeg", byteSize, "checksum", NOW);
	}

	private MediaAssetStatus statusOf(MediaAsset asset) {
		return repository.findById(asset.getId()).orElseThrow().getStatus();
	}

	private void assertStorageUnavailable(long mediaId) {
		assertThatThrownBy(() -> service.confirm(mediaId, 1L))
				.isInstanceOf(AnswerException.class)
				.hasFieldOrPropertyWithValue("errorCode", AnswerErrorCode.STORAGE_UNAVAILABLE);
	}

	private static final class FakeObjectStoragePort implements ObjectStoragePort {
		private final Map<String, StoredObjectMetadata> objects = new HashMap<>();
		private final Map<String, byte[]> bodies = new HashMap<>();
		private int headCalls;
		private int readCalls;
		private int putCalls;
		private boolean failRead;
		private boolean failPut;

		/** 메타데이터 크기와 본문이 일치하는 정상 업로드. */
		void put(String key, String contentType, byte[] body) {
			putRaw(key, body.length, contentType, body);
		}

		/** HeadObject가 돌려줄 크기와 실제 본문을 따로 정한다. */
		void putRaw(String key, long size, String contentType, byte[] body) {
			objects.put(key, new StoredObjectMetadata(size, contentType));
			bodies.put(key, body.clone());
		}

		void remove(String key) {
			objects.remove(key);
			bodies.remove(key);
		}

		byte[] bodyOf(String key) {
			return bodies.get(key);
		}

		String contentTypeOf(String key) {
			return objects.get(key).contentType();
		}

		int calls() {
			return headCalls + readCalls + putCalls;
		}

		@Override
		public PresignedView issueGetUrl(String storageKey, Duration ttl) {
			try {
				URL url = URI.create("https://example-test.invalid/get/" + storageKey).toURL();
				return new PresignedView(url, Instant.EPOCH.plus(ttl));
			} catch (MalformedURLException exception) {
				throw new IllegalStateException(exception);
			}
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
			headCalls++;
			return Optional.ofNullable(objects.get(storageKey));
		}

		@Override
		public Optional<byte[]> readObjectPrefix(String storageKey, int maxBytes) {
			readCalls++;
			if (failRead) {
				throw new AnswerException(AnswerErrorCode.STORAGE_UNAVAILABLE, null, "미디어 본문 조회에 실패했습니다");
			}
			return Optional.ofNullable(bodies.get(storageKey))
					.map(body -> Arrays.copyOf(body, Math.min(body.length, maxBytes)));
		}

		@Override
		public void putObject(String storageKey, String contentType, byte[] body) {
			putCalls++;
			if (failPut) {
				throw new AnswerException(AnswerErrorCode.STORAGE_UNAVAILABLE, null, "미디어 저장에 실패했습니다");
			}
			put(storageKey, contentType, body);
		}
	}

	private static final class InMemoryMediaAssetRepository implements MediaAssetRepository {
		private final Map<Long, MediaAsset> store = new HashMap<>();
		private long nextId = 1;

		@Override
		public MediaAsset save(MediaAsset asset) {
			long id = asset.getId() != null ? asset.getId() : nextId++;
			MediaAsset persisted = MediaAsset.restore(id, asset.getOwnerId(), asset.getStatus(), asset.getStorageKey(),
					asset.getMimeType(), asset.getByteSize(), asset.getChecksum(), asset.isExifStripped(),
					asset.getCreatedAt(),
					asset.getDeletedAt());
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
