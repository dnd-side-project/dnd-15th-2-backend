/**
 * Created at: 2026-10-07T23:25:28+09:00
 * Source scenario: TEST-PLAN-GH-325-EXIF-STRIP-INT-001 through INT-005, INT-007, INT-009
 */
package com.dnd.qello;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpRequest.BodyPublishers;
import java.net.http.HttpResponse;
import java.net.http.HttpResponse.BodyHandlers;
import java.nio.ByteOrder;
import java.time.Instant;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;

import com.dnd.qello.ExifTestImages.JpegSegment;
import com.dnd.qello.ExifTestImages.PngLayout;
import com.dnd.qello.account.service.ProfileService;
import com.dnd.qello.answer.domain.MediaAsset;
import com.dnd.qello.answer.domain.MediaAssetStatus;
import com.dnd.qello.answer.domain.MediaStorageKeys;
import com.dnd.qello.answer.repository.MediaAssetRepository;
import com.dnd.qello.answer.service.MediaUploadService;
import com.dnd.qello.answer.service.MediaUploadService.IssueUploadUrlCommand;
import com.dnd.qello.answer.service.MediaUploadService.UploadUrl;
import com.dnd.qello.answer.service.port.PresignedUpload;

import static com.dnd.qello.ExifTestImages.APP1;
import static com.dnd.qello.ExifTestImages.TAG_ORIENTATION;
import static com.dnd.qello.ExifTestImages.XMP_GPS;
import static com.dnd.qello.ExifTestImages.contains;
import static com.dnd.qello.ExifTestImages.headerSegments;
import static com.dnd.qello.ExifTestImages.ifd0;
import static com.dnd.qello.ExifTestImages.phoneTiff;
import static com.dnd.qello.ExifTestImages.pngLayout;
import static com.dnd.qello.ExifTestImages.tiffOf;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.entry;

import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.model.HeadObjectRequest;
import software.amazon.awssdk.services.s3.model.NoSuchKeyException;
import software.amazon.awssdk.services.s3.model.S3Exception;

@SpringBootTest
@ActiveProfiles("test")
@ExtendWith(OutputCaptureExtension.class)
class ExifStripIntegrationTest extends LocalStackContainerIntegrationTestSupport {

	private static final String REGION = "TEST-EXIF-STRIP";
	private static final Instant NOW = Instant.parse("2026-10-07T00:00:00Z");
	private static final HttpClient HTTP_CLIENT = HttpClient.newHttpClient();

	@Autowired
	private JdbcTemplate jdbc;
	@Autowired
	private MediaUploadService mediaUploadService;
	@Autowired
	private MediaAssetRepository mediaAssetRepository;
	@Autowired
	private ProfileService profileService;

	private long ownerId;

	@BeforeEach
	void resetFixtures() {
		jdbc.update("UPDATE user_account SET profile_image_media_id = NULL WHERE coarse_region_code = ?", REGION);
		jdbc.update(
				"DELETE FROM media_asset WHERE owner_id IN (SELECT id FROM user_account WHERE coarse_region_code = ?)",
				REGION);
		jdbc.update("DELETE FROM user_account WHERE coarse_region_code = ?", REGION);
		jdbc.update("DELETE FROM region_code WHERE code = ?", REGION);
		jdbc.update("""
				INSERT INTO region_code (code, parent_code, display_name, level)
				VALUES ('KR', NULL, 'Korea', 'COUNTRY') ON CONFLICT (code, level) DO NOTHING
				""");
		jdbc.update("""
				INSERT INTO region_code (code, parent_code, display_name, level)
				VALUES (?, 'KR', 'Exif Strip Test', 'REGION')
				""", REGION);
		ownerId = jdbc.queryForObject("""
				INSERT INTO user_account (role, country_code, status, coarse_region_code, locale, timezone, nickname)
				VALUES ('USER', 'KR', 'ACTIVE', ?, 'ko-KR', 'Asia/Seoul', 'exif-owner')
				RETURNING id
				""", Long.class, REGION);
	}

	@Test
	@DisplayName("INT-001 GPS가 든 JPEG을 confirm하면 serving key에 Orientation만 남은 처리본을 두고 READY·exif_stripped로 바뀐다")
	void confirmStoresStrippedJpegUnderServingKey() throws Exception {
		byte[] original = gpsJpeg();
		Uploaded uploaded = upload("image/jpeg", original);

		MediaAsset confirmed = mediaUploadService.confirm(uploaded.mediaId(), ownerId);

		assertThat(confirmed.getStatus()).isEqualTo(MediaAssetStatus.READY);
		Map<String, Object> row = jdbc.queryForMap(
				"SELECT status, storage_key, exif_stripped, byte_size, checksum FROM media_asset WHERE id = ?",
				uploaded.mediaId());
		String servingKey = MediaStorageKeys.servingKeyOf(uploaded.uploadKey());
		assertThat(row).containsEntry("status", "READY").containsEntry("storage_key", servingKey)
				.containsEntry("exif_stripped", true).containsEntry("byte_size", (long) original.length)
				.containsEntry("checksum", "checksum");
		assertThat(servingKey).isNotEqualTo(uploaded.uploadKey());
		assertOnlyOrientationJpeg(readObject(servingKey), 6);
		assertThat(contentTypeOf(servingKey)).isEqualTo("image/jpeg");
	}

	@Test
	@DisplayName("INT-002 eXIf와 텍스트 청크가 든 PNG를 confirm하면 처리본은 Orientation만 담은 eXIf와 허용 청크만 가진다")
	void confirmStoresStrippedPngUnderServingKey() throws Exception {
		byte[] original = ExifTestImages.png().exif(phoneTiff(ByteOrder.BIG_ENDIAN, 6))
				.text("tEXt", "Comment\0exif-strip-test")
				.text("iTXt", "XML:com.adobe.xmp\0\0\0\0\0" + XMP_GPS)
				.build();
		Uploaded uploaded = upload("image/png", original);

		MediaAsset confirmed = mediaUploadService.confirm(uploaded.mediaId(), ownerId);

		assertThat(confirmed.getStatus()).isEqualTo(MediaAssetStatus.READY);
		PngLayout layout = pngLayout(readObject(confirmed.getStorageKey()));
		assertThat(layout.types()).doesNotContain("tEXt", "iTXt").contains("IHDR", "IDAT", "IEND");
		assertThat(ifd0(layout.first("eXIf").data()).tags()).containsExactly(entry(TAG_ORIENTATION, 6));
		assertThat(layout.chunks()).allMatch(ExifTestImages.PngChunk::crcValid);
	}

	@Test
	@DisplayName("INT-003 READY 뒤 같은 presigned URL로 다른 GPS 사진을 다시 올려도 서빙 객체는 처음 처리본 그대로다")
	void reuploadingAfterReadyDoesNotChangeServedObject() throws Exception {
		byte[] original = gpsJpeg();
		byte[] replacement = ExifTestImages.jpeg().exif(phoneTiff(ByteOrder.LITTLE_ENDIAN, 3)).comment("replaced")
				.build();
		Uploaded uploaded = upload("image/jpeg", original);
		MediaAsset first = mediaUploadService.confirm(uploaded.mediaId(), ownerId);
		byte[] servedBefore = readObject(first.getStorageKey());

		putViaPresignedUrl(uploaded.presignedUpload(), "image/jpeg", replacement);
		MediaAsset second = mediaUploadService.confirm(uploaded.mediaId(), ownerId);

		assertThat(readObject(uploaded.uploadKey())).isEqualTo(replacement);
		assertThat(second.getStatus()).isEqualTo(MediaAssetStatus.READY);
		assertThat(second.getStorageKey()).isEqualTo(first.getStorageKey());
		assertThat(readObject(second.getStorageKey())).isEqualTo(servedBefore);
		assertOnlyOrientationJpeg(servedBefore, 6);
	}

	@Test
	@DisplayName("INT-004 confirm한 이미지를 프로필로 지정하면 프로필 조회 URL로 받은 파일에 GPS가 없다")
	void profileImageUrlServesStrippedObject() throws Exception {
		Uploaded uploaded = upload("image/jpeg", gpsJpeg());
		MediaAsset confirmed = mediaUploadService.confirm(uploaded.mediaId(), ownerId);

		ProfileService.Profile profile = profileService.changeProfileImage(ownerId, uploaded.mediaId());
		HttpResponse<byte[]> response = HTTP_CLIENT.send(
				HttpRequest.newBuilder(profile.profileImage().url().toURI()).GET().build(), BodyHandlers.ofByteArray());

		assertThat(profile.profileImage().usesDefaultImage()).isFalse();
		assertThat(response.statusCode()).isEqualTo(200);
		assertThat(response.body()).isEqualTo(readObject(confirmed.getStorageKey()));
		assertOnlyOrientationJpeg(response.body(), 6);
	}

	@Test
	@DisplayName("INT-005 시그니처만 맞고 구조가 깨진 JPEG은 REJECTED이고 serving key에 객체를 만들지 않는다")
	void malformedJpegIsRejectedWithoutServedObject() throws Exception {
		byte[] malformed = Arrays.copyOf(new byte[]{(byte) 0xFF, (byte) 0xD8, (byte) 0xFF, (byte) 0xE1, (byte) 0xFF,
				(byte) 0xFF}, 64);
		Uploaded uploaded = upload("image/jpeg", malformed);

		MediaAsset confirmed = mediaUploadService.confirm(uploaded.mediaId(), ownerId);

		assertThat(confirmed.getStatus()).isEqualTo(MediaAssetStatus.REJECTED);
		assertThat(jdbc.queryForObject("SELECT exif_stripped FROM media_asset WHERE id = ?", Boolean.class,
				uploaded.mediaId())).isFalse();
		assertThat(exists(MediaStorageKeys.servingKeyOf(uploaded.uploadKey()))).isFalse();
	}

	@Test
	@DisplayName("INT-007 confirm 처리와 거절 중 출력된 로그에 upload key, serving key, 버킷 이름이 없다")
	void confirmDoesNotLogStorageIdentifiers(CapturedOutput output) throws Exception {
		Uploaded stripped = upload("image/jpeg", gpsJpeg());
		Uploaded rejected = upload("image/jpeg", Arrays.copyOf(new byte[]{(byte) 0xFF, (byte) 0xD8, (byte) 0xFF}, 32));

		mediaUploadService.confirm(stripped.mediaId(), ownerId);
		mediaUploadService.confirm(rejected.mediaId(), ownerId);

		for (Uploaded uploaded : List.of(stripped, rejected)) {
			assertThat(output.getAll()).doesNotContain(uploaded.uploadKey())
					.doesNotContain(MediaStorageKeys.servingKeyOf(uploaded.uploadKey()));
		}
		assertThat(output.getAll()).doesNotContain(TEST_BUCKET);
	}

	@Test
	@DisplayName("INT-009 이 규칙 전에 READY가 된 행(exif_stripped=false)은 예외 없이 읽힌다")
	void legacyReadyRowWithoutStrippedFlagIsReadable() {
		long mediaId = jdbc.queryForObject("""
				INSERT INTO media_asset (owner_id, status, storage_key, mime_type, byte_size, checksum, exif_stripped)
				VALUES (?, 'READY', ?, 'image/jpeg', 10, 'checksum', FALSE)
				RETURNING id
				""", Long.class, ownerId, "media/" + ownerId + "/legacy-" + System.nanoTime());

		MediaAsset legacy = mediaAssetRepository.findById(mediaId).orElseThrow();

		assertThat(legacy.getStatus()).isEqualTo(MediaAssetStatus.READY);
		assertThat(legacy.isExifStripped()).isFalse();
	}

	private static byte[] gpsJpeg() {
		return ExifTestImages.jpeg().exif(phoneTiff(ByteOrder.BIG_ENDIAN, 6)).xmp(XMP_GPS).build();
	}

	private Uploaded upload(String contentType, byte[] body) throws Exception {
		UploadUrl issued = mediaUploadService.issueUploadUrl(
				new IssueUploadUrlCommand(ownerId, ownerId, contentType, body.length, "checksum", NOW));
		putViaPresignedUrl(issued.presignedUpload(), contentType, body);
		return new Uploaded(issued.asset().getId(), issued.asset().getStorageKey(), issued.presignedUpload());
	}

	private static void assertOnlyOrientationJpeg(byte[] served, int orientation) {
		List<JpegSegment> exif = headerSegments(served).stream()
				.filter(segment -> segment.marker() == APP1 && segment.hasId("Exif\0\0")).toList();
		assertThat(exif).hasSize(1);
		assertThat(ifd0(tiffOf(exif.get(0))).tags()).containsExactly(entry(TAG_ORIENTATION, orientation));
		assertThat(headerSegments(served)).noneMatch(segment -> segment.marker() == APP1 && !segment.hasId("Exif"));
		assertThat(contains(served, "GPSLatitude")).isFalse();
		assertThat(contains(served, "TestMake")).isFalse();
	}

	private void putViaPresignedUrl(PresignedUpload presignedUpload, String contentType, byte[] body)
			throws Exception {
		HttpRequest request = HttpRequest.newBuilder(presignedUpload.url().toURI())
				.header("Content-Type", contentType)
				.PUT(BodyPublishers.ofByteArray(body))
				.build();
		assertThat(HTTP_CLIENT.send(request, BodyHandlers.discarding()).statusCode()).isEqualTo(200);
	}

	private static byte[] readObject(String key) {
		try (S3Client client = testS3Client()) {
			return client.getObjectAsBytes(GetObjectRequest.builder().bucket(TEST_BUCKET).key(key).build())
					.asByteArray();
		}
	}

	private static String contentTypeOf(String key) {
		try (S3Client client = testS3Client()) {
			return client.headObject(HeadObjectRequest.builder().bucket(TEST_BUCKET).key(key).build()).contentType();
		}
	}

	private static boolean exists(String key) {
		try (S3Client client = testS3Client()) {
			client.headObject(HeadObjectRequest.builder().bucket(TEST_BUCKET).key(key).build());
			return true;
		} catch (NoSuchKeyException exception) {
			return false;
		} catch (S3Exception exception) {
			if (exception.statusCode() == 404) {
				return false;
			}
			throw exception;
		}
	}

	private record Uploaded(long mediaId, String uploadKey, PresignedUpload presignedUpload) {
	}
}
