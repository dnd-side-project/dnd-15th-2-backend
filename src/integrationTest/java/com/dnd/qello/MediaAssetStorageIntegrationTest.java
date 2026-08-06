/**
 * Created at: 2026-08-07T03:30:00+09:00
 * Source scenario: TEST-PLAN-GH-70-MEDIA-ASSET-SERVICE-INT-001 through INT-005, INT-010 through INT-011
 */
package com.dnd.qello;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpRequest.BodyPublishers;
import java.net.http.HttpResponse;
import java.net.http.HttpResponse.BodyHandlers;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;

import com.dnd.qello.answer.domain.MediaAsset;
import com.dnd.qello.answer.domain.MediaAssetStatus;
import com.dnd.qello.answer.repository.MediaAssetRepository;
import com.dnd.qello.answer.service.MediaUploadService;
import com.dnd.qello.answer.service.MediaUploadService.IssueUploadUrlCommand;
import com.dnd.qello.answer.service.MediaUploadService.UploadUrl;
import com.dnd.qello.answer.service.port.PresignedUpload;

import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;

@SpringBootTest
@ActiveProfiles("test")
class MediaAssetStorageIntegrationTest extends LocalStackContainerIntegrationTestSupport {

	private static final String REGION = "TEST-MEDIA-STORAGE";
	private static final Instant NOW = Instant.parse("2026-08-07T00:00:00Z");
	private static final HttpClient HTTP_CLIENT = HttpClient.newHttpClient();

	@Autowired
	private JdbcTemplate jdbc;
	@Autowired
	private MediaUploadService mediaUploadService;
	@Autowired
	private MediaAssetRepository mediaAssetRepository;

	private long ownerId;

	@BeforeEach
	void resetFixtures() {
		jdbc.update("DELETE FROM media_asset");
		jdbc.update("DELETE FROM user_account WHERE coarse_region_code = ?", REGION);
		jdbc.update("DELETE FROM region_code WHERE code = ?", REGION);
		jdbc.update("INSERT INTO region_code (code, display_name, level) VALUES (?, 'Media Storage Test', 'COUNTRY')", REGION);
		ownerId = account("media-owner");
	}

	private long account(String nickname) {
		return jdbc.queryForObject("""
			INSERT INTO user_account (role, status, coarse_region_code, locale, timezone, nickname)
			VALUES ('USER', 'ACTIVE', ?, 'ko-KR', 'Asia/Seoul', ?)
			RETURNING id
			""", Long.class, REGION, nickname);
	}

	@Test
	@DisplayName("presigned URL 발급은 UPLOADING media_asset을 유일한 storage_key로 생성한다")
	void issuesUploadUrlAndCreatesUploadingAsset() {
		UploadUrl result = mediaUploadService.issueUploadUrl(
			new IssueUploadUrlCommand(ownerId, ownerId, "image/jpeg", 11L, "checksum", NOW));

		MediaAsset stored = mediaAssetRepository.findById(result.asset().getId()).orElseThrow();
		assertThat(stored.getStatus()).isEqualTo(MediaAssetStatus.UPLOADING);
		assertThat(stored.getStorageKey()).isNotBlank();
		assertThat(result.presignedUpload().url()).isNotNull();
	}

	@Test
	@DisplayName("presigned URL로 실제 업로드 후 confirm하면 크기·타입이 일치할 때 READY로 전이한다")
	void confirmTransitionsToReadyWhenUploadedObjectMatches() throws Exception {
		byte[] body = "hello-world".getBytes(StandardCharsets.UTF_8);
		UploadUrl issued = mediaUploadService.issueUploadUrl(
			new IssueUploadUrlCommand(ownerId, ownerId, "image/jpeg", body.length, "checksum", NOW));

		putViaPresignedUrl(issued.presignedUpload(), "image/jpeg", body);
		MediaAsset confirmed = mediaUploadService.confirm(issued.asset().getId(), ownerId);

		assertThat(confirmed.getStatus()).isEqualTo(MediaAssetStatus.READY);
	}

	@Test
	@DisplayName("업로드된 객체가 없으면 confirm은 REJECTED로 전이한다")
	void confirmRejectsWhenObjectMissing() {
		UploadUrl issued = mediaUploadService.issueUploadUrl(
			new IssueUploadUrlCommand(ownerId, ownerId, "image/jpeg", 5L, "checksum", NOW));

		MediaAsset confirmed = mediaUploadService.confirm(issued.asset().getId(), ownerId);

		assertThat(confirmed.getStatus()).isEqualTo(MediaAssetStatus.REJECTED);
	}

	@Test
	@DisplayName("업로드된 객체 크기가 신고 값과 다르면 confirm은 REJECTED로 전이한다")
	void confirmRejectsWhenSizeMismatches() throws Exception {
		byte[] declaredSizeBody = "12345".getBytes(StandardCharsets.UTF_8);
		byte[] actualUploadedBody = "1234567890".getBytes(StandardCharsets.UTF_8);
		UploadUrl issued = mediaUploadService.issueUploadUrl(new IssueUploadUrlCommand(
			ownerId, ownerId, "image/jpeg", declaredSizeBody.length, "checksum", NOW));

		putViaPresignedUrl(issued.presignedUpload(), "image/jpeg", actualUploadedBody);
		MediaAsset confirmed = mediaUploadService.confirm(issued.asset().getId(), ownerId);

		assertThat(confirmed.getStatus()).isEqualTo(MediaAssetStatus.REJECTED);
	}

	@Test
	@DisplayName("업로드된 객체 content-type이 신고 값과 다르면 confirm은 REJECTED로 전이한다")
	void confirmRejectsWhenContentTypeMismatches() {
		byte[] body = "12345".getBytes(StandardCharsets.UTF_8);
		UploadUrl issued = mediaUploadService.issueUploadUrl(new IssueUploadUrlCommand(
			ownerId, ownerId, "image/png", body.length, "checksum", NOW));

		// presigned URL의 서명에는 발급 시 지정한 content-type이 포함되므로, 다른
		// content-type으로는 그 URL을 통한 업로드 자체가 거부된다(서명 불일치). 이 시나리오는
		// "이미 실제와 다른 타입으로 저장된 객체"를 재현하기 위해 presigned URL을 우회하고
		// 테스트 전용 S3Client로 직접 업로드한다.
		try (S3Client client = testS3Client()) {
			client.putObject(PutObjectRequest.builder().bucket(TEST_BUCKET).key(issued.asset().getStorageKey())
				.contentType("application/octet-stream").build(), RequestBody.fromBytes(body));
		}

		MediaAsset confirmed = mediaUploadService.confirm(issued.asset().getId(), ownerId);

		assertThat(confirmed.getStatus()).isEqualTo(MediaAssetStatus.REJECTED);
	}

	@Test
	@DisplayName("동시에 confirm을 두 번 호출해도 한 번만 상태를 확정하고 둘 다 같은 결과를 멱등하게 반환한다")
	void concurrentConfirmIsIdempotent() throws Exception {
		byte[] body = "concurrent".getBytes(StandardCharsets.UTF_8);
		UploadUrl issued = mediaUploadService.issueUploadUrl(new IssueUploadUrlCommand(
			ownerId, ownerId, "image/jpeg", body.length, "checksum", NOW));
		putViaPresignedUrl(issued.presignedUpload(), "image/jpeg", body);

		ExecutorService executor = Executors.newFixedThreadPool(2);
		CountDownLatch ready = new CountDownLatch(2);
		CountDownLatch start = new CountDownLatch(1);
		try {
			List<Future<MediaAsset>> futures = List.of(
				executor.submit(() -> confirmSynchronized(issued.asset().getId(), ownerId, ready, start)),
				executor.submit(() -> confirmSynchronized(issued.asset().getId(), ownerId, ready, start)));
			ready.await(5, TimeUnit.SECONDS);
			start.countDown();
			List<MediaAssetStatus> results = futures.stream().map(this::join).map(MediaAsset::getStatus).toList();

			assertThat(results).containsExactly(MediaAssetStatus.READY, MediaAssetStatus.READY);
			assertThat(mediaAssetRepository.findById(issued.asset().getId()).orElseThrow().getStatus())
				.isEqualTo(MediaAssetStatus.READY);
		} finally {
			executor.shutdownNow();
		}
	}

	@Test
	@DisplayName("같은 storage_key로 두 media_asset을 만들면 두 번째는 unique 제약으로 실패한다")
	void storageKeyCollisionIsRejectedByUniqueConstraint() {
		String sharedKey = "media/" + ownerId + "/forced-collision";
		MediaAsset first = mediaAssetRepository.save(
			MediaAsset.upload(ownerId, sharedKey, "image/jpeg", 10L, "checksum", NOW));

		assertThat(first.getId()).isNotNull();
		assertThatThrownBy(() -> mediaAssetRepository.save(
				MediaAsset.upload(ownerId, sharedKey, "image/jpeg", 20L, "checksum2", NOW)))
			.isInstanceOf(DataIntegrityViolationException.class);
	}

	private MediaAsset confirmSynchronized(long mediaId, long requesterId, CountDownLatch ready, CountDownLatch start) {
		ready.countDown();
		awaitUninterruptibly(start);
		return mediaUploadService.confirm(mediaId, requesterId);
	}

	private static void awaitUninterruptibly(CountDownLatch latch) {
		try {
			latch.await(5, TimeUnit.SECONDS);
		} catch (InterruptedException exception) {
			Thread.currentThread().interrupt();
			throw new IllegalStateException(exception);
		}
	}

	private MediaAsset join(Future<MediaAsset> future) {
		try {
			return future.get(5, TimeUnit.SECONDS);
		} catch (Exception exception) {
			throw new IllegalStateException(exception);
		}
	}

	private void putViaPresignedUrl(PresignedUpload presignedUpload, String contentType, byte[] body) throws Exception {
		HttpRequest request = HttpRequest.newBuilder(URI.create(presignedUpload.url().toString()))
			.header("Content-Type", contentType)
			.PUT(BodyPublishers.ofByteArray(body))
			.build();
		HttpResponse<String> response = HTTP_CLIENT.send(request, BodyHandlers.ofString());
		assertThat(response.statusCode())
			.as("presigned PUT 실패: " + response.statusCode() + " " + preview(response.body()))
			.isEqualTo(200);
	}

	private static String preview(String body) {
		return body == null ? "" : body.lines().collect(Collectors.joining(" ")).substring(0, Math.min(body.length(), 200));
	}
}
