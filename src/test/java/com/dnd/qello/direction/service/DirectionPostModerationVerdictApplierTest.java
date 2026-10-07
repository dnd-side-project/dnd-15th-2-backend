/**
 * Created at: 2026-10-07T22:12:28+09:00
 * Source scenario: TEST-PLAN-GH-137-DIRECTION-POST-MODERATION-UNIT-009 through UNIT-010
 */
package com.dnd.qello.direction.service;

import java.time.Instant;
import java.util.Optional;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import com.dnd.qello.direction.domain.DirectionPost;
import com.dnd.qello.direction.domain.DirectionPostModerationStatus;
import com.dnd.qello.direction.domain.DirectionPostStatus;
import com.dnd.qello.direction.domain.DirectionRequestFingerprint;
import com.dnd.qello.direction.repository.DirectionPostRepository;
import com.dnd.qello.filtering.domain.FilterTargetType;
import com.dnd.qello.filtering.domain.FilterVerdict;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class DirectionPostModerationVerdictApplierTest {

	private static final long POST_ID = 31L;
	private static final Instant SUBMITTED_AT = Instant.parse("2026-10-07T08:00:00Z");
	private static final Instant NOW = Instant.parse("2026-10-07T08:05:00Z");

	private final DirectionPostRepository postRepository = mock(DirectionPostRepository.class);
	private final DirectionPostModerationVerdictApplier applier = new DirectionPostModerationVerdictApplier(
			postRepository);

	@Test
	@DisplayName("TEST-PLAN-GH-137-DIRECTION-POST-MODERATION-UNIT-009: 잠근 PENDING 질문글에 ALLOW·BLOCK·deadline을 반영하면 바뀐 상태를 한 번 저장한다")
	void savesLockedPostOnlyWhenTransitioned() {
		when(postRepository.findByIdForUpdate(POST_ID))
			.thenReturn(Optional.of(post(DirectionPostModerationStatus.PENDING)));

		applier.applyVerdict(POST_ID, FilterVerdict.ALLOW, NOW);
		applier.applyVerdict(POST_ID, FilterVerdict.BLOCK, NOW);
		applier.applyDeadlineElapsed(POST_ID, NOW);

		ArgumentCaptor<DirectionPost> saved = ArgumentCaptor.forClass(DirectionPost.class);
		verify(postRepository, org.mockito.Mockito.times(3)).save(saved.capture());
		assertThat(saved.getAllValues()).extracting(DirectionPost::getModerationStatus)
			.containsExactly(DirectionPostModerationStatus.PASSED, DirectionPostModerationStatus.REJECTED,
				DirectionPostModerationStatus.REVIEW_HELD);
		assertThat(applier.targetType()).isEqualTo(FilterTargetType.DIRECTION_POST);
	}

	@Test
	@DisplayName("TEST-PLAN-GH-137-DIRECTION-POST-MODERATION-UNIT-009: 이미 PASSED인 질문글에 다시 온 ALLOW·BLOCK·deadline은 저장하지 않는다")
	void doesNotSaveWhenNoTransition() {
		when(postRepository.findByIdForUpdate(POST_ID))
			.thenReturn(Optional.of(post(DirectionPostModerationStatus.PASSED)));

		applier.applyVerdict(POST_ID, FilterVerdict.ALLOW, NOW);
		applier.applyVerdict(POST_ID, FilterVerdict.BLOCK, NOW);
		applier.applyDeadlineElapsed(POST_ID, NOW);

		verify(postRepository, never()).save(any(DirectionPost.class));
	}

	@Test
	@DisplayName("TEST-PLAN-GH-137-DIRECTION-POST-MODERATION-UNIT-010: 질문글이 없으면 예외 없이 끝나 이벤트가 무한 재처리되지 않는다")
	void missingPostEndsWithoutException() {
		when(postRepository.findByIdForUpdate(POST_ID)).thenReturn(Optional.empty());

		applier.applyVerdict(POST_ID, FilterVerdict.ALLOW, NOW);
		applier.applyDeadlineElapsed(POST_ID, NOW);

		verify(postRepository, never()).save(any(DirectionPost.class));
	}

	private static DirectionPost post(DirectionPostModerationStatus moderationStatus) {
		DirectionRequestFingerprint fingerprint = DirectionRequestFingerprint.create(101L, 202L, "S0", 0, 5_000L,
				"applier body");
		return DirectionPost.restore(POST_ID, 11L, 101L, fingerprint, DirectionPostStatus.MATCHING, "applier-key",
				"applier body", "TEST-REGION", moderationStatus, SUBMITTED_AT, null, SUBMITTED_AT.plusSeconds(3600),
				null,
				null);
	}
}
