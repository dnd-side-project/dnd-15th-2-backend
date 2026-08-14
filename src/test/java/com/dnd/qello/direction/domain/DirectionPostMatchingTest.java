/**
 * Created at: 2026-08-13T17:25:00+09:00
 * Source scenario: TEST-PLAN-GH-120-DIRECTION-MATCHING-WORKER-UNIT-001 through UNIT-003
 */
package com.dnd.qello.direction.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Instant;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.dnd.qello.direction.error.DirectionException;

class DirectionPostMatchingTest {

	private static final Instant SUBMITTED_AT = Instant.parse("2026-08-13T08:00:00Z");
	private static final Instant DEADLINE = Instant.parse("2026-08-13T09:00:00Z");
	private static final Instant NOW = Instant.parse("2026-08-13T08:30:00Z");

	@Test
	@DisplayName("PASSED 상태의 MATCHING 질문글은 deadline 전에 ACTIVE로 전이된다")
	void activatesPassedMatchingPostBeforeDeadline() {
		DirectionPost post = post(DirectionPostModerationStatus.PASSED, DirectionPostStatus.MATCHING, DEADLINE);

		DirectionPost active = post.activate(NOW);

		assertThat(active.getStatus()).isEqualTo(DirectionPostStatus.ACTIVE);
		assertThat(active.getPublishedAt()).isEqualTo(NOW);
		assertThat(active.getModerationStatus()).isEqualTo(DirectionPostModerationStatus.PASSED);
		assertThat(active.getExpiresAt()).isEqualTo(DEADLINE);
	}

	@Test
	@DisplayName("deadline과 같거나 지난 질문글은 선택 A에 따라 매칭할 수 없다")
	void rejectsMatchingAtOrAfterDeadline() {
		DirectionPost post = post(DirectionPostModerationStatus.PASSED, DirectionPostStatus.MATCHING, DEADLINE);

		assertThat(post.canMatchAt(DEADLINE)).isFalse();
		assertThat(post.canMatchAt(DEADLINE.plusSeconds(1))).isFalse();
		assertThat(post.expire(DEADLINE).getStatus()).isEqualTo(DirectionPostStatus.EXPIRED);
	}

	@Test
	@DisplayName("만료 전 질문글은 현재 시각을 기준으로만 EXPIRED 전이를 허용한다")
	void rejectsInvalidExpireTimeBeforeDeadline() {
		DirectionPost post = post(DirectionPostModerationStatus.PASSED, DirectionPostStatus.MATCHING, DEADLINE);

		assertThatThrownBy(() -> post.expire(NOW))
			.isInstanceOf(DirectionException.class);
	}

	@Test
	@DisplayName("moderation이 PASSED가 아니면 질문글은 매칭할 수 없다")
	void failsClosedForModerationStatuses() {
		for (DirectionPostModerationStatus moderation : DirectionPostModerationStatus.values()) {
			DirectionPost post = post(moderation, DirectionPostStatus.MATCHING, DEADLINE);

			assertThat(post.canMatchAt(NOW))
				.as("moderation=%s", moderation)
				.isEqualTo(moderation == DirectionPostModerationStatus.PASSED);
		}
	}

	private DirectionPost post(DirectionPostModerationStatus moderationStatus,
		DirectionPostStatus status, Instant expiresAt) {
		DirectionRequestFingerprint fingerprint = DirectionRequestFingerprint.create(
			101L, 202L, "S0", 0, 5_000L, "matching body");
		return DirectionPost.restore(1L, 11L, 101L, fingerprint, status, "matching-key",
			"matching body", "TEST-REGION", moderationStatus, SUBMITTED_AT,
			status == DirectionPostStatus.ACTIVE ? NOW : null, expiresAt, null, null);
	}
}
