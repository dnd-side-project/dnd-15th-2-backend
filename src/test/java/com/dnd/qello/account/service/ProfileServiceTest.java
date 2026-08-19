/**
 * Created at: 2026-08-18T23:33:15+09:00
 * Source scenario: TEST-PLAN-GH-166-PROFILE-IMAGE-UPLOAD-UNIT-005 through UNIT-011,
 * UNIT-013, UNIT-015, UNIT-017, UNIT-018
 */
package com.dnd.qello.account.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.net.URI;
import java.net.URL;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.dnd.qello.account.domain.Account;
import com.dnd.qello.account.domain.AccountRole;
import com.dnd.qello.account.domain.AccountStatus;
import com.dnd.qello.account.repository.AccountRepository;
import com.dnd.qello.account.service.ProfileImageResolver.ResolvedProfileImage;
import com.dnd.qello.answer.config.MediaStorageProperties;
import com.dnd.qello.answer.domain.ImageMimeType;
import com.dnd.qello.answer.domain.MediaAsset;
import com.dnd.qello.answer.domain.MediaAssetStatus;
import com.dnd.qello.answer.error.AnswerErrorCode;
import com.dnd.qello.answer.error.AnswerException;
import com.dnd.qello.answer.repository.MediaAssetRepository;
import com.dnd.qello.answer.service.port.ObjectStoragePort;
import com.dnd.qello.answer.service.port.PresignedUpload;
import com.dnd.qello.answer.service.port.PresignedView;
import com.dnd.qello.answer.service.port.StoredObjectMetadata;

class ProfileServiceTest {

	private static final long OWNER_ID = 1L;
	private static final long OTHER_ID = 2L;
	private static final Instant CREATED_AT = Instant.parse("2026-08-18T00:00:00Z");
	private static final String DEFAULT_KEY = "media/defaults/profile-image.png";
	private static final Duration VIEW_TTL = Duration.ofMinutes(5);

	private final MediaStorageProperties properties = new MediaStorageProperties(
		"test-bucket", ImageMimeType.supportedMimeTypes(), 1_000L, Duration.ofMinutes(10), VIEW_TTL, DEFAULT_KEY);
	private final FakeAccountRepository accounts = new FakeAccountRepository();
	private final FakeMediaAssetRepository assets = new FakeMediaAssetRepository();
	private final RecordingObjectStoragePort storage = new RecordingObjectStoragePort();
	private final ProfileImageResolver resolver = new ProfileImageResolver(assets, storage, properties);
	private final ProfileService service = new ProfileService(accounts, assets, resolver);

	@Test
	@DisplayName("다른 사용자가 소유한 READY 자산은 존재를 드러내지 않고 미디어 없음으로 거절한다")
	void rejectsAssetOwnedByAnotherUser() {
		accounts.store(activeUser(OWNER_ID));
		assets.store(readyAsset(10L, OTHER_ID));

		assertThatThrownBy(() -> service.changeProfileImage(OWNER_ID, 10L))
			.isInstanceOf(AnswerException.class)
			.hasFieldOrPropertyWithValue("errorCode", AnswerErrorCode.MEDIA_NOT_FOUND);
		assertThat(accounts.find(OWNER_ID).getProfileImageMediaId()).isNull();
	}

	@Test
	@DisplayName("UPLOADING 자산은 객체 존재가 보장되지 않으므로 프로필로 지정할 수 없다")
	void rejectsUploadingAsset() {
		accounts.store(activeUser(OWNER_ID));
		assets.store(asset(10L, OWNER_ID, MediaAssetStatus.UPLOADING, null));

		assertThatThrownBy(() -> service.changeProfileImage(OWNER_ID, 10L))
			.isInstanceOf(AnswerException.class)
			.hasFieldOrPropertyWithValue("errorCode", AnswerErrorCode.INVALID_MEDIA_STATUS);
	}

	@Test
	@DisplayName("REJECTED 자산은 업로드 확인에 실패한 자산이므로 프로필로 지정할 수 없다")
	void rejectsRejectedAsset() {
		accounts.store(activeUser(OWNER_ID));
		assets.store(asset(10L, OWNER_ID, MediaAssetStatus.REJECTED, null));

		assertThatThrownBy(() -> service.changeProfileImage(OWNER_ID, 10L))
			.isInstanceOf(AnswerException.class)
			.hasFieldOrPropertyWithValue("errorCode", AnswerErrorCode.INVALID_MEDIA_STATUS);
	}

	@Test
	@DisplayName("DELETED 자산은 프로필로 새로 지정할 수 없다")
	void rejectsDeletedAsset() {
		accounts.store(activeUser(OWNER_ID));
		assets.store(asset(10L, OWNER_ID, MediaAssetStatus.DELETED, CREATED_AT));

		assertThatThrownBy(() -> service.changeProfileImage(OWNER_ID, 10L))
			.isInstanceOf(AnswerException.class)
			.hasFieldOrPropertyWithValue("errorCode", AnswerErrorCode.INVALID_MEDIA_STATUS);
	}

	@Test
	@DisplayName("존재하지 않는 미디어 식별자는 미디어 없음으로 거절한다")
	void rejectsMissingAsset() {
		accounts.store(activeUser(OWNER_ID));

		assertThatThrownBy(() -> service.changeProfileImage(OWNER_ID, 999L))
			.isInstanceOf(AnswerException.class)
			.hasFieldOrPropertyWithValue("errorCode", AnswerErrorCode.MEDIA_NOT_FOUND);
	}

	@Test
	@DisplayName("프로필 이미지를 설정하지 않은 계정은 기본 이미지 키로 조회 URL을 발급한다")
	void issuesDefaultImageUrlWhenUnset() {
		accounts.store(activeUser(OWNER_ID));

		ResolvedProfileImage image = service.getProfile(OWNER_ID).profileImage();

		assertThat(storage.requestedKeys).containsExactly(DEFAULT_KEY);
		assertThat(image.usesDefaultImage()).isTrue();
	}

	@Test
	@DisplayName("프로필 이미지가 설정된 계정은 해당 자산의 storage key로 조회 URL을 발급한다")
	void issuesOwnImageUrlWhenSet() {
		accounts.store(activeUser(OWNER_ID).withProfileImage(10L));
		assets.store(readyAsset(10L, OWNER_ID));

		ResolvedProfileImage image = service.getProfile(OWNER_ID).profileImage();

		assertThat(storage.requestedKeys).containsExactly("media/1/own-image");
		assertThat(image.usesDefaultImage()).isFalse();
	}

	@Test
	@DisplayName("조회 URL은 업로드 TTL이 아니라 조회 전용 TTL로 발급한다")
	void usesViewTtlNotUploadTtl() {
		accounts.store(activeUser(OWNER_ID));

		service.getProfile(OWNER_ID);

		assertThat(storage.requestedTtls).containsExactly(VIEW_TTL);
		assertThat(VIEW_TTL).isNotEqualTo(properties.uploadUrlTtl());
	}

	@Test
	@DisplayName("프로필에 붙은 자산이 DELETED가 되면 조회는 기본 이미지로 폴백하고 참조는 남긴다")
	void fallsBackToDefaultWhenAttachedAssetBecomesDeleted() {
		accounts.store(activeUser(OWNER_ID).withProfileImage(10L));
		assets.store(asset(10L, OWNER_ID, MediaAssetStatus.DELETED, CREATED_AT));

		ResolvedProfileImage image = service.getProfile(OWNER_ID).profileImage();

		assertThat(storage.requestedKeys).containsExactly(DEFAULT_KEY);
		assertThat(image.usesDefaultImage()).isTrue();
		assertThat(accounts.find(OWNER_ID).getProfileImageMediaId()).isEqualTo(10L);
	}

	@Test
	@DisplayName("프로필 이미지 삭제는 참조만 끊고 media asset의 상태를 바꾸지 않는다")
	void removesReferenceWithoutTouchingAsset() {
		accounts.store(activeUser(OWNER_ID).withProfileImage(10L));
		assets.store(readyAsset(10L, OWNER_ID));

		service.removeProfileImage(OWNER_ID);

		assertThat(accounts.find(OWNER_ID).getProfileImageMediaId()).isNull();
		assertThat(assets.find(10L).getStatus()).isEqualTo(MediaAssetStatus.READY);
		assertThat(assets.savedCount).isZero();
	}

	@Test
	@DisplayName("본인 소유 READY 자산은 프로필로 지정되고 그 자산의 조회 URL을 돌려준다")
	void changesProfileImageToOwnedReadyAsset() {
		accounts.store(activeUser(OWNER_ID));
		assets.store(readyAsset(10L, OWNER_ID));

		ResolvedProfileImage image = service.changeProfileImage(OWNER_ID, 10L).profileImage();

		assertThat(accounts.find(OWNER_ID).getProfileImageMediaId()).isEqualTo(10L);
		assertThat(image.usesDefaultImage()).isFalse();
	}

	@Test
	@DisplayName("저장소 장애는 스택트레이스가 아니라 저장소 사용 불가 오류로 변환한다")
	void translatesStorageFailure() {
		accounts.store(activeUser(OWNER_ID));
		storage.failing = true;

		assertThatThrownBy(() -> service.getProfile(OWNER_ID))
			.isInstanceOf(AnswerException.class)
			.hasFieldOrPropertyWithValue("errorCode", AnswerErrorCode.STORAGE_UNAVAILABLE);
	}

	private static Account activeUser(long id) {
		return Account.restore(id, AccountRole.USER, AccountStatus.ACTIVE, "KR", "KR-TEST",
			"ko-KR", "Asia/Seoul", "qello-user", null);
	}

	private static MediaAsset readyAsset(long id, long ownerId) {
		return MediaAsset.restore(id, ownerId, MediaAssetStatus.READY, "media/" + ownerId + "/own-image",
			"image/png", 100L, "checksum", CREATED_AT, null);
	}

	private static MediaAsset asset(long id, long ownerId, MediaAssetStatus status, Instant deletedAt) {
		return MediaAsset.restore(id, ownerId, status, "media/" + ownerId + "/own-image",
			"image/png", 100L, "checksum", CREATED_AT, deletedAt);
	}

	private static final class FakeAccountRepository implements AccountRepository {
		private final Map<Long, Account> stored = new HashMap<>();

		void store(Account account) {
			stored.put(account.getId(), account);
		}

		Account find(long id) {
			return stored.get(id);
		}

		@Override
		public Account save(Account account) {
			throw new UnsupportedOperationException();
		}

		@Override
		public Account updateProfile(Account account) {
			throw new UnsupportedOperationException();
		}

		@Override
		public Account updateProfileImage(Account account) {
			stored.put(account.getId(), account);
			return account;
		}

		@Override
		public Account updateStatus(Account account) {
			throw new UnsupportedOperationException();
		}

		@Override
		public Optional<Account> findById(long id) {
			return Optional.ofNullable(stored.get(id));
		}
	}

	private static final class FakeMediaAssetRepository implements MediaAssetRepository {
		private final Map<Long, MediaAsset> stored = new HashMap<>();
		private int savedCount;

		void store(MediaAsset asset) {
			stored.put(asset.getId(), asset);
		}

		MediaAsset find(long id) {
			return stored.get(id);
		}

		@Override
		public MediaAsset save(MediaAsset asset) {
			savedCount++;
			stored.put(asset.getId(), asset);
			return asset;
		}

		@Override
		public Optional<MediaAsset> findById(long id) {
			throw new AssertionError("사용자 입력 경로에서 소유권 없는 조회를 호출하면 안 된다");
		}

		@Override
		public Optional<MediaAsset> findByIdAndOwnerId(long id, long ownerId) {
			return Optional.ofNullable(stored.get(id)).filter(asset -> asset.getOwnerId() == ownerId);
		}

		@Override
		public Optional<MediaAsset> transitionFromUploading(MediaAsset next) {
			throw new UnsupportedOperationException();
		}
	}

	private static final class RecordingObjectStoragePort implements ObjectStoragePort {
		private final List<String> requestedKeys = new ArrayList<>();
		private final List<Duration> requestedTtls = new ArrayList<>();
		private boolean failing;

		@Override
		public PresignedUpload issuePutUrl(String storageKey, String contentType, Duration ttl) {
			throw new UnsupportedOperationException();
		}

		@Override
		public PresignedView issueGetUrl(String storageKey, Duration ttl) {
			if (failing) {
				throw new AnswerException(
					AnswerErrorCode.STORAGE_UNAVAILABLE, null, "조회 URL 발급에 실패했습니다");
			}
			requestedKeys.add(storageKey);
			requestedTtls.add(ttl);
			return new PresignedView(url(storageKey), CREATED_AT.plus(ttl));
		}

		@Override
		public Optional<StoredObjectMetadata> headObject(String storageKey) {
			throw new UnsupportedOperationException();
		}

		@Override
		public Optional<byte[]> readObjectPrefix(String storageKey, int maxBytes) {
			throw new UnsupportedOperationException();
		}

		private static URL url(String storageKey) {
			try {
				return URI.create("https://example-test.invalid/" + storageKey + "?signed").toURL();
			} catch (Exception exception) {
				throw new IllegalStateException(exception);
			}
		}
	}
}
