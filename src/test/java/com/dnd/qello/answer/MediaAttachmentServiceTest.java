/**
 * Created at: 2026-08-14T16:05:00+09:00
 * Source scenario: TEST-PLAN-GH-122-DIRECTION-PREVIEW-SUBMISSION-API-UNIT-009
 */
package com.dnd.qello.answer;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.dnd.qello.answer.domain.Answer;
import com.dnd.qello.answer.domain.AnswerStatus;
import com.dnd.qello.answer.domain.MediaAsset;
import com.dnd.qello.answer.domain.MediaAssetStatus;
import com.dnd.qello.answer.domain.MediaAttachment;
import com.dnd.qello.answer.error.AnswerErrorCode;
import com.dnd.qello.answer.error.AnswerException;
import com.dnd.qello.answer.repository.AnswerRepository;
import com.dnd.qello.answer.repository.MediaAssetRepository;
import com.dnd.qello.answer.repository.MediaAttachmentRepository;
import com.dnd.qello.answer.service.MediaAttachmentService;
import com.dnd.qello.answer.service.MediaAttachmentService.AttachCommand;
import com.dnd.qello.direction.domain.DirectionPost;
import com.dnd.qello.direction.domain.DirectionPostStatus;
import com.dnd.qello.direction.repository.DirectionPostRepository;

class MediaAttachmentServiceTest {

	private static final Instant NOW = Instant.parse("2026-08-07T00:00:00Z");
	private static final long OWNER = 1L;
	private static final long STRANGER = 2L;

	private InMemoryMediaAssetRepository mediaAssetRepository;
	private InMemoryMediaAttachmentRepository mediaAttachmentRepository;
	private InMemoryDirectionPostRepository directionPostRepository;
	private InMemoryAnswerRepository answerRepository;
	private MediaAttachmentService service;

	@BeforeEach
	void setUp() {
		mediaAssetRepository = new InMemoryMediaAssetRepository();
		mediaAttachmentRepository = new InMemoryMediaAttachmentRepository();
		directionPostRepository = new InMemoryDirectionPostRepository();
		answerRepository = new InMemoryAnswerRepository();
		service = new MediaAttachmentService(
			mediaAssetRepository, mediaAttachmentRepository, directionPostRepository, answerRepository);
	}

	@Test
	@DisplayName("MediaAttachment는 post/answer 중 정확히 하나만 대상으로 허용한다")
	void validatesExactlyOneTarget() {
		MediaAttachment postAttachment = new MediaAttachment(1L, OWNER, 10L, null, 0);
		assertThat(postAttachment.postId()).isEqualTo(10L);

		assertThatThrownBy(() -> new MediaAttachment(1L, OWNER, 10L, 20L, 0))
			.isInstanceOf(AnswerException.class)
			.hasFieldOrPropertyWithValue("errorCode", AnswerErrorCode.INVALID_MEDIA_TARGET);
		assertThatThrownBy(() -> new MediaAttachment(1L, OWNER, null, null, 0))
			.isInstanceOf(AnswerException.class)
			.hasFieldOrPropertyWithValue("errorCode", AnswerErrorCode.INVALID_MEDIA_TARGET);
		assertThatThrownBy(() -> new MediaAttachment(0L, OWNER, 10L, null, 0))
			.isInstanceOf(AnswerException.class)
			.hasFieldOrPropertyWithValue("errorCode", AnswerErrorCode.INVALID_ID);
		assertThatThrownBy(() -> new AttachCommand(OWNER, 1L, null, null, 0))
			.isInstanceOf(AnswerException.class)
			.hasFieldOrPropertyWithValue("errorCode", AnswerErrorCode.INVALID_MEDIA_TARGET);
	}

	@Test
	@DisplayName("본인 소유가 아닌 미디어는 attach 시도 시 찾을 수 없는 것으로 처리된다")
	void attachRejectsMediaNotOwnedByRequester() {
		MediaAsset ready = readyMedia(OWNER);
		directionPostRepository.put(post(OWNER, DirectionPostStatus.ACTIVE, null));

		assertThatThrownBy(() -> service.attach(new AttachCommand(STRANGER, ready.getId(), OWNER_POST_ID, null, 0)))
			.isInstanceOf(AnswerException.class)
			.hasFieldOrPropertyWithValue("errorCode", AnswerErrorCode.MEDIA_NOT_FOUND);
	}

	@Test
	@DisplayName("READY 상태가 아닌 미디어는 attach할 수 없다")
	void attachRejectsNonReadyMedia() {
		MediaAsset uploading = mediaAssetRepository.save(
			MediaAsset.upload(OWNER, "key", "image/jpeg", 10L, "checksum", NOW));
		directionPostRepository.put(post(OWNER, DirectionPostStatus.ACTIVE, null));

		assertThatThrownBy(() -> service.attach(new AttachCommand(OWNER, uploading.getId(), OWNER_POST_ID, null, 0)))
			.isInstanceOf(AnswerException.class)
			.hasFieldOrPropertyWithValue("errorCode", AnswerErrorCode.INVALID_MEDIA_STATUS);
	}

	@Test
	@DisplayName("본인 소유 미디어라도 남의 질문글에는 첨부할 수 없다")
	void attachRejectsWhenTargetPostIsNotOwnedByRequester() {
		MediaAsset ready = readyMedia(OWNER);
		directionPostRepository.put(post(STRANGER, DirectionPostStatus.ACTIVE, null));

		assertThatThrownBy(() -> service.attach(new AttachCommand(OWNER, ready.getId(), OWNER_POST_ID, null, 0)))
			.isInstanceOf(AnswerException.class)
			.hasFieldOrPropertyWithValue("errorCode", AnswerErrorCode.MEDIA_OWNER_MISMATCH);
	}

	@Test
	@DisplayName("본인 소유 READY 미디어를 본인 질문글에 첨부하면 성공한다")
	void attachSucceedsForOwnMediaAndOwnPost() {
		MediaAsset ready = readyMedia(OWNER);
		directionPostRepository.put(post(OWNER, DirectionPostStatus.ACTIVE, null));

		MediaAttachment attachment = service.attach(new AttachCommand(OWNER, ready.getId(), OWNER_POST_ID, null, 0));

		assertThat(attachment.mediaId()).isEqualTo(ready.getId());
		assertThat(mediaAttachmentRepository.findByMediaId(ready.getId())).isPresent();
	}

	@Test
	@DisplayName("이미 첨부된 READY 미디어는 다른 콘텐츠에 재사용할 수 없다")
	void attachRejectsAlreadyAttachedMedia() {
		MediaAsset ready = readyMedia(OWNER);
		directionPostRepository.put(post(OWNER, DirectionPostStatus.ACTIVE, "본문 있음"));
		service.attach(new AttachCommand(OWNER, ready.getId(), OWNER_POST_ID, null, 0));

		assertThatThrownBy(() -> service.attach(new AttachCommand(OWNER, ready.getId(), OWNER_POST_ID, null, 0)))
			.isInstanceOf(AnswerException.class)
			.hasFieldOrPropertyWithValue("errorCode", AnswerErrorCode.INVALID_MEDIA_STATUS);
	}

	@Test
	@DisplayName("공개된 질문글에서 본문도 없고 유일한 READY 미디어를 해제하면 거부된다")
	void detachRejectsWhenItWouldLeaveActivePostWithoutContent() {
		MediaAsset ready = readyMedia(OWNER);
		directionPostRepository.put(post(OWNER, DirectionPostStatus.ACTIVE, null));
		service.attach(new AttachCommand(OWNER, ready.getId(), OWNER_POST_ID, null, 0));

		assertThatThrownBy(() -> service.detach(ready.getId(), OWNER))
			.isInstanceOf(AnswerException.class)
			.hasFieldOrPropertyWithValue("errorCode", AnswerErrorCode.MEDIA_CONTENT_REQUIRED);
	}

	@Test
	@DisplayName("본문이 있으면 유일한 READY 미디어를 해제해도 허용된다")
	void detachSucceedsWhenPostHasBody() {
		MediaAsset ready = readyMedia(OWNER);
		directionPostRepository.put(post(OWNER, DirectionPostStatus.ACTIVE, "본문 있음"));
		service.attach(new AttachCommand(OWNER, ready.getId(), OWNER_POST_ID, null, 0));

		service.detach(ready.getId(), OWNER);

		assertThat(mediaAttachmentRepository.findByMediaId(ready.getId())).isEmpty();
	}

	@Test
	@DisplayName("아직 공개되지 않은(ACTIVE 이전) 질문글은 본문·미디어 요건을 강제하지 않는다")
	void detachAllowsRemovingSoleMediaBeforePostIsActive() {
		MediaAsset ready = readyMedia(OWNER);
		directionPostRepository.put(post(OWNER, DirectionPostStatus.MATCHING, null));
		service.attach(new AttachCommand(OWNER, ready.getId(), OWNER_POST_ID, null, 0));

		service.detach(ready.getId(), OWNER);

		assertThat(mediaAttachmentRepository.findByMediaId(ready.getId())).isEmpty();
	}

	private static final long OWNER_POST_ID = 100L;
	private static final long OWNER_ANSWER_ID = 200L;

	private MediaAsset readyMedia(long ownerId) {
		MediaAsset uploading = mediaAssetRepository.save(
			MediaAsset.upload(ownerId, "media/" + ownerId + "/key", "image/jpeg", 10L, "checksum", NOW));
		MediaAsset ready = uploading.ready();
		mediaAssetRepository.save(ready);
		return ready;
	}

	private DirectionPost post(long senderId, DirectionPostStatus status, String bodyText) {
		if (status == DirectionPostStatus.ACTIVE) {
			return DirectionPost.restore(OWNER_POST_ID, senderId, 1L, status, "key-" + senderId, bodyText, "TEST",
				com.dnd.qello.direction.domain.DirectionPostModerationStatus.PASSED, NOW, NOW,
				NOW.plusSeconds(3600), null, null);
		}
		return DirectionPost.restore(OWNER_POST_ID, senderId, 1L, status, "key-" + senderId, bodyText, "TEST",
			com.dnd.qello.direction.domain.DirectionPostModerationStatus.PENDING, NOW, null,
			NOW.plusSeconds(3600), null, null);
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
		public Optional<MediaAsset> findById(long id) { return Optional.ofNullable(store.get(id)); }

		@Override
		public Optional<MediaAsset> findByIdAndOwnerId(long id, long ownerId) {
			return findById(id).filter(asset -> asset.getOwnerId() == ownerId);
		}

		@Override
		public Optional<MediaAsset> transitionFromUploading(MediaAsset next) {
			throw new UnsupportedOperationException("not used in this test");
		}
	}

	private static final class InMemoryMediaAttachmentRepository implements MediaAttachmentRepository {
		private final Map<Long, MediaAttachment> store = new HashMap<>();

		@Override
		public MediaAttachment save(MediaAttachment attachment) {
			store.put(attachment.mediaId(), attachment);
			return attachment;
		}

		@Override
		public Optional<MediaAttachment> findByMediaId(long mediaId) { return Optional.ofNullable(store.get(mediaId)); }

		@Override
		public Optional<MediaAttachment> findByMediaIdAndOwnerId(long mediaId, long ownerId) {
			return findByMediaId(mediaId).filter(attachment -> attachment.ownerId() == ownerId);
		}

		@Override
		public List<Long> findMediaIdsByPostId(long postId) {
			return store.values().stream()
				.filter(attachment -> attachment.postId() != null && attachment.postId() == postId)
				.sorted(java.util.Comparator.comparingInt(MediaAttachment::displayOrder))
				.map(MediaAttachment::mediaId)
				.toList();
		}

		@Override
		public void deleteByMediaId(long mediaId) { store.remove(mediaId); }

		@Override
		public boolean existsOtherReadyMediaForPost(long postId, long excludingMediaId) {
			return false;
		}

		@Override
		public boolean existsOtherReadyMediaForAnswer(long answerId, long excludingMediaId) {
			return false;
		}
	}

	private static final class InMemoryDirectionPostRepository implements DirectionPostRepository {
		private final Map<Long, DirectionPost> store = new HashMap<>();

		void put(DirectionPost post) { store.put(post.getId(), post); }

		@Override
		public DirectionPost save(DirectionPost post) { throw new UnsupportedOperationException("not used"); }

		@Override
		public Optional<DirectionPost> findById(long id) { return Optional.ofNullable(store.get(id)); }

		@Override
		public Optional<DirectionPost> findBySenderAndIdempotencyKey(long senderId, String idempotencyKey) {
			throw new UnsupportedOperationException("not used");
		}

		@Override
		public Optional<DirectionPost> findByIdAndSenderId(long id, long senderId) {
			return findById(id).filter(post -> post.getSenderId() == senderId);
		}

		@Override
		public DirectionPost advanceAnswersReadAt(long id, Instant at) {
			throw new UnsupportedOperationException("not used");
		}
	}

	private static final class InMemoryAnswerRepository implements AnswerRepository {
		private final Map<Long, Answer> store = new HashMap<>();

		@Override
		public Answer save(Answer answer) { throw new UnsupportedOperationException("not used"); }

		@Override
		public Optional<Answer> findById(long id) { return Optional.ofNullable(store.get(id)); }

		@Override
		public Optional<Answer> findByAuthorAndIdempotencyKey(long authorId, String idempotencyKey) {
			throw new UnsupportedOperationException("not used");
		}

		@Override
		public Optional<Answer> findByIdAndAuthorId(long id, long authorId) {
			return findById(id).filter(answer -> answer.getAuthorId() == authorId);
		}
	}
}
