/**
 * Created at: 2026-08-18T23:33:15+09:00
 * Source scenario: TEST-PLAN-GH-166-PROFILE-IMAGE-UPLOAD-INT-001 through INT-004,
 * INT-007, INT-009 through INT-011, INT-013
 */
package com.dnd.qello;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.http.HttpResponse.BodyHandlers;
import java.time.Instant;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import com.dnd.qello.account.service.ProfileImageResolver.ResolvedProfileImage;
import com.dnd.qello.account.service.ProfileService;
import com.dnd.qello.answer.config.MediaStorageProperties;
import com.dnd.qello.answer.domain.MediaAssetStatus;
import com.dnd.qello.answer.error.AnswerErrorCode;
import com.dnd.qello.answer.error.AnswerException;
import com.dnd.qello.answer.service.MediaUploadService;
import com.dnd.qello.answer.service.MediaUploadService.IssueUploadUrlCommand;
import com.dnd.qello.answer.service.MediaUploadService.UploadUrl;

import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class ProfileImageIntegrationTest extends LocalStackContainerIntegrationTestSupport {

	private static final String REGION = "TEST-PROFILE-IMAGE";
	private static final Instant NOW = Instant.parse("2026-08-18T00:00:00Z");
	private static final HttpClient HTTP_CLIENT = HttpClient.newHttpClient();
	// 1x1 PNG. confirm 단계가 실제 객체의 시그니처까지 확인하므로 진짜 PNG 바이트가 필요하다.
	private static final byte[] PNG = {
		(byte) 0x89, 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A,
		0x00, 0x00, 0x00, 0x0D, 0x49, 0x48, 0x44, 0x52,
		0x00, 0x00, 0x00, 0x01, 0x00, 0x00, 0x00, 0x01,
		0x08, 0x06, 0x00, 0x00, 0x00, 0x1F, 0x15, (byte) 0xC4,
		(byte) 0x89
	};

	@Autowired
	private JdbcTemplate jdbc;
	@Autowired
	private MockMvc mockMvc;
	@Autowired
	private ProfileService profileService;
	@Autowired
	private MediaUploadService mediaUploadService;
	@Autowired
	private MediaStorageProperties properties;

	private long ownerId;
	private long otherId;

	@BeforeEach
	void resetFixtures() {
		jdbc.update("UPDATE user_account SET profile_image_media_id = NULL");
		jdbc.update("DELETE FROM media_asset");
		jdbc.update("DELETE FROM user_account WHERE coarse_region_code = ?", REGION);
		jdbc.update("DELETE FROM region_code WHERE code = ?", REGION);
		jdbc.update("""
			INSERT INTO region_code (code, parent_code, display_name, level)
			VALUES ('KR', NULL, 'Korea', 'COUNTRY') ON CONFLICT (code, level) DO NOTHING
			""");
		jdbc.update("""
			INSERT INTO region_code (code, parent_code, display_name, level)
			VALUES (?, 'KR', 'Profile Image Test', 'REGION')
			""", REGION);
		ownerId = account("profile-owner");
		otherId = account("profile-other");
		putObject(properties.defaultProfileImageKey());
	}

	@Test
	@DisplayName("V21은 profile_image_media_id를 nullable로 추가하고 기존 계정은 기본 이미지 상태로 남는다")
	void addsNullableProfileImageColumn() {
		String nullable = jdbc.queryForObject("""
			SELECT is_nullable FROM information_schema.columns
			WHERE table_name = 'user_account' AND column_name = 'profile_image_media_id'
			""", String.class);
		Long unset = jdbc.queryForObject(
			"SELECT count(*) FROM user_account WHERE id = ? AND profile_image_media_id IS NULL",
			Long.class, ownerId);

		assertThat(nullable).isEqualTo("YES");
		assertThat(unset).isEqualTo(1L);
	}

	@Test
	@DisplayName("복합 FK가 남의 자산을 프로필로 지정하는 직접 UPDATE를 막는다")
	void compositeForeignKeyRejectsAssetOwnedByAnotherUser() {
		long otherAsset = readyAsset(otherId);

		assertThatThrownBy(() -> jdbc.update(
			"UPDATE user_account SET profile_image_media_id = ? WHERE id = ?", otherAsset, ownerId))
			.isInstanceOf(DataIntegrityViolationException.class);
	}

	@Test
	@DisplayName("업로드·확인을 마친 본인 자산을 프로필로 지정하면 그 이미지의 조회 URL을 받는다")
	void servesOwnImageAfterUploadAndConfirm() throws Exception {
		long mediaId = uploadAndConfirm(ownerId);

		ResolvedProfileImage image = profileService.changeProfileImage(ownerId, mediaId).profileImage();

		assertThat(image.usesDefaultImage()).isFalse();
		assertThat(download(image)).isEqualTo(200);
		assertThat(image.expiresAt()).isAfter(Instant.now());
	}

	@Test
	@DisplayName("프로필 이미지를 설정하지 않은 계정은 실제로 내려받을 수 있는 기본 이미지 URL을 받는다")
	void servesDownloadableDefaultImage() throws Exception {
		ResolvedProfileImage image = profileService.getProfile(ownerId).profileImage();

		assertThat(image.usesDefaultImage()).isTrue();
		assertThat(download(image)).isEqualTo(200);
	}

	@Test
	@DisplayName("프로필 이미지를 삭제하면 기본 이미지로 돌아가고 media asset은 READY로 남는다")
	void removingProfileImageKeepsAsset() throws Exception {
		long mediaId = uploadAndConfirm(ownerId);
		profileService.changeProfileImage(ownerId, mediaId);

		ResolvedProfileImage image = profileService.removeProfileImage(ownerId).profileImage();

		assertThat(image.usesDefaultImage()).isTrue();
		assertThat(statusOf(mediaId)).isEqualTo(MediaAssetStatus.READY.name());
		assertThat(profileImageIdOf(ownerId)).isNull();
	}

	@Test
	@DisplayName("프로필에 붙은 자산이 DELETED가 되면 조회는 기본 이미지로 폴백하고 참조는 남는다")
	void fallsBackToDefaultWhenAttachedAssetIsDeleted() throws Exception {
		long mediaId = uploadAndConfirm(ownerId);
		profileService.changeProfileImage(ownerId, mediaId);
		jdbc.update("UPDATE media_asset SET status = 'DELETED', deleted_at = now() WHERE id = ?", mediaId);

		ResolvedProfileImage image = profileService.getProfile(ownerId).profileImage();

		assertThat(image.usesDefaultImage()).isTrue();
		assertThat(profileImageIdOf(ownerId)).isEqualTo(mediaId);
	}

	@Test
	@DisplayName("남의 자산을 프로필로 지정하려 하면 거절하고 프로필을 바꾸지 않는다")
	void rejectsAssetOwnedByAnotherUserThroughService() throws Exception {
		long otherAsset = uploadAndConfirm(otherId);

		assertThatThrownBy(() -> profileService.changeProfileImage(ownerId, otherAsset))
			.isInstanceOf(AnswerException.class)
			.hasFieldOrPropertyWithValue("errorCode", AnswerErrorCode.MEDIA_NOT_FOUND);
		assertThat(profileImageIdOf(ownerId)).isNull();
	}

	@Test
	@DisplayName("인증 없이 프로필을 조회하면 401로 차단한다")
	void requiresAuthentication() throws Exception {
		mockMvc.perform(get("/api/v1/me/profile")).andExpect(status().isUnauthorized());
	}

	private long uploadAndConfirm(long userId) {
		UploadUrl upload = mediaUploadService.issueUploadUrl(
			new IssueUploadUrlCommand(userId, userId, "image/png", (long) PNG.length, "checksum", NOW));
		long mediaId = upload.asset().getId();
		putObject(upload.asset().getStorageKey());
		assertThat(mediaUploadService.confirm(mediaId, userId).getStatus())
			.isEqualTo(MediaAssetStatus.READY);
		return mediaId;
	}

	private long readyAsset(long userId) {
		return jdbc.queryForObject("""
			INSERT INTO media_asset (owner_id, status, storage_key, mime_type, byte_size, checksum)
			VALUES (?, 'READY', ?, 'image/png', 33, 'checksum')
			RETURNING id
			""", Long.class, userId, "media/" + userId + "/direct-" + System.nanoTime());
	}

	private void putObject(String storageKey) {
		try (S3Client client = testS3Client()) {
			client.putObject(PutObjectRequest.builder()
				.bucket(TEST_BUCKET).key(storageKey).contentType("image/png").build(),
				RequestBody.fromBytes(PNG));
		}
	}

	private int download(ResolvedProfileImage image) throws Exception {
		HttpResponse<byte[]> response = HTTP_CLIENT.send(
			HttpRequest.newBuilder(URI.create(image.url().toExternalForm())).GET().build(),
			BodyHandlers.ofByteArray());
		return response.statusCode();
	}

	private String statusOf(long mediaId) {
		return jdbc.queryForObject("SELECT status FROM media_asset WHERE id = ?", String.class, mediaId);
	}

	private Long profileImageIdOf(long userId) {
		return jdbc.queryForObject(
			"SELECT profile_image_media_id FROM user_account WHERE id = ?", Long.class, userId);
	}

	private long account(String nickname) {
		return jdbc.queryForObject("""
			INSERT INTO user_account (role, country_code, status, coarse_region_code, locale, timezone, nickname)
			VALUES ('USER', 'KR', 'ACTIVE', ?, 'ko-KR', 'Asia/Seoul', ?)
			RETURNING id
			""", Long.class, REGION, nickname);
	}
}
