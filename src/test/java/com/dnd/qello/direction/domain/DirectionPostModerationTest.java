/**
 * Created at: 2026-10-07T22:11:55+09:00
 * Source scenario: TEST-PLAN-GH-137-DIRECTION-POST-MODERATION-UNIT-001 through UNIT-008
 */
package com.dnd.qello.direction.domain;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.function.BiFunction;
import java.util.stream.Stream;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.MethodSource;

import static org.assertj.core.api.Assertions.assertThat;

class DirectionPostModerationTest {

	private static final Instant SUBMITTED_AT = Instant.parse("2026-10-07T08:00:00Z");
	private static final Instant EXPIRES_AT = Instant.parse("2026-10-07T20:00:00Z");
	private static final Instant NOW = Instant.parse("2026-10-07T08:05:00Z");

	@Test
	@DisplayName("TEST-PLAN-GH-137-DIRECTION-POST-MODERATION-UNIT-001: 만료 전 PENDING 질문글은 ALLOW로 PASSED가 되어 매칭 가능해지고 status는 유지된다")
	void allowPassesPendingPost() {
		DirectionPost post = post(DirectionPostStatus.MATCHING, DirectionPostModerationStatus.PENDING, EXPIRES_AT);

		DirectionPost passed = post.passModeration(NOW).orElseThrow();

		assertThat(passed.getModerationStatus()).isEqualTo(DirectionPostModerationStatus.PASSED);
		assertThat(passed.getStatus()).isEqualTo(DirectionPostStatus.MATCHING);
		assertThat(passed.canMatchAt(NOW)).isTrue();
		assertThat(passed.getId()).isEqualTo(post.getId());
		assertThat(passed.getBodyText()).isEqualTo(post.getBodyText());
		assertThat(passed.getExpiresAt()).isEqualTo(EXPIRES_AT);
	}

	@Test
	@DisplayName("TEST-PLAN-GH-137-DIRECTION-POST-MODERATION-UNIT-002: PENDING 질문글은 BLOCK으로 REJECTED가 되고 status는 MATCHING으로 남는다(D1)")
	void blockRejectsPendingPostWithoutChangingStatus() {
		DirectionPost post = post(DirectionPostStatus.MATCHING, DirectionPostModerationStatus.PENDING, EXPIRES_AT);

		DirectionPost rejected = post.rejectModeration(NOW).orElseThrow();

		assertThat(rejected.getModerationStatus()).isEqualTo(DirectionPostModerationStatus.REJECTED);
		assertThat(rejected.getStatus()).isEqualTo(DirectionPostStatus.MATCHING);
		assertThat(rejected.canMatchAt(NOW)).isFalse();
	}

	@Test
	@DisplayName("TEST-PLAN-GH-137-DIRECTION-POST-MODERATION-UNIT-003: PENDING 질문글은 deadline 신호로 REVIEW_HELD가 되고 매칭할 수 없다")
	void deadlineHoldsPendingPostForReview() {
		DirectionPost post = post(DirectionPostStatus.MATCHING, DirectionPostModerationStatus.PENDING, EXPIRES_AT);

		DirectionPost held = post.holdModerationForReview(NOW).orElseThrow();

		assertThat(held.getModerationStatus()).isEqualTo(DirectionPostModerationStatus.REVIEW_HELD);
		assertThat(held.getStatus()).isEqualTo(DirectionPostStatus.MATCHING);
		assertThat(held.canMatchAt(NOW)).isFalse();
	}

	@ParameterizedTest(name = "{0}")
	@MethodSource("reviewHeldVerdicts")
	@DisplayName("TEST-PLAN-GH-137-DIRECTION-POST-MODERATION-UNIT-004: REVIEW_HELD 질문글은 늦게 도착한 판정(수동 검토 포함)으로 PASSED·REJECTED가 된다")
	void lateVerdictResolvesReviewHeldPost(String verdict,
			BiFunction<DirectionPost, Instant, Optional<DirectionPost>> transition,
			DirectionPostModerationStatus expected) {
		DirectionPost post = post(DirectionPostStatus.MATCHING, DirectionPostModerationStatus.REVIEW_HELD, EXPIRES_AT);

		assertThat(transition.apply(post, NOW)).get()
				.extracting(DirectionPost::getModerationStatus)
				.isEqualTo(expected);
	}

	static Stream<Arguments> reviewHeldVerdicts() {
		return Stream.of(
				Arguments.of("ALLOW", passModeration(), DirectionPostModerationStatus.PASSED),
				Arguments.of("BLOCK", rejectModeration(), DirectionPostModerationStatus.REJECTED));
	}

	@ParameterizedTest(name = "{0} <- {1}")
	@MethodSource("decidedPostsAndSignals")
	@DisplayName("TEST-PLAN-GH-137-DIRECTION-POST-MODERATION-UNIT-005: 이미 PASSED·REJECTED인 질문글은 중복·늦은 판정이나 deadline 신호로 바뀌지 않는다")
	void decidedPostIgnoresAnyLaterSignal(DirectionPostModerationStatus decided, String signal,
			BiFunction<DirectionPost, Instant, Optional<DirectionPost>> transition) {
		DirectionPost post = post(DirectionPostStatus.MATCHING, decided, EXPIRES_AT);

		assertThat(transition.apply(post, NOW)).isEmpty();
	}

	static Stream<Arguments> decidedPostsAndSignals() {
		return Stream.of(DirectionPostModerationStatus.PASSED, DirectionPostModerationStatus.REJECTED)
				.flatMap(decided -> Stream.of(
						Arguments.of(decided, "ALLOW", passModeration()),
						Arguments.of(decided, "BLOCK", rejectModeration()),
						Arguments.of(decided, "DEADLINE_ELAPSED", holdForReview())));
	}

	@Test
	@DisplayName("TEST-PLAN-GH-137-DIRECTION-POST-MODERATION-UNIT-006: 이미 REVIEW_HELD인 질문글에 다시 온 deadline 신호는 상태를 바꾸지 않는다")
	void repeatedDeadlineKeepsReviewHeld() {
		DirectionPost post = post(DirectionPostStatus.MATCHING, DirectionPostModerationStatus.REVIEW_HELD, EXPIRES_AT);

		assertThat(post.holdModerationForReview(NOW)).isEmpty();
	}

	@ParameterizedTest(name = "{0}")
	@MethodSource("closedPendingPosts")
	@DisplayName("TEST-PLAN-GH-137-DIRECTION-POST-MODERATION-UNIT-007: 만료 시각이 지났거나 매칭 대기 중이 아닌 PENDING 질문글은 늦은 ALLOW로 PASSED가 되지 않는다")
	void lateAllowDoesNotReopenClosedPost(String label, DirectionPost post) {
		assertThat(post.passModeration(NOW)).isEmpty();
		assertThat(post.rejectModeration(NOW)).isEmpty();
		assertThat(post.holdModerationForReview(NOW)).isEmpty();
	}

	static Stream<Arguments> closedPendingPosts() {
		return Stream.of(
				Arguments.of("expiresAt == now",
						post(DirectionPostStatus.MATCHING, DirectionPostModerationStatus.PENDING, NOW)),
				Arguments.of("expiresAt < now",
						post(DirectionPostStatus.MATCHING, DirectionPostModerationStatus.PENDING,
								NOW.minusSeconds(1))),
				Arguments.of("EXPIRED",
						post(DirectionPostStatus.EXPIRED, DirectionPostModerationStatus.PENDING, EXPIRES_AT)),
				Arguments.of("DELETED",
						post(DirectionPostStatus.DELETED, DirectionPostModerationStatus.PENDING, EXPIRES_AT)));
	}

	@ParameterizedTest
	@EnumSource(value = DirectionPostModerationStatus.class, names = {"PASSED", "PENDING"})
	@DisplayName("TEST-PLAN-GH-137-DIRECTION-POST-MODERATION-UNIT-008: 본문 없는 질문글(미디어 단독)은 PASSED로, 본문 있는 질문글은 PENDING으로 생성된다")
	void submitDecidesInitialModerationByBody(DirectionPostModerationStatus expected) {
		String body = expected == DirectionPostModerationStatus.PASSED ? null : "본문";
		DirectionRequestFingerprint fingerprint = DirectionRequestFingerprint.create(101L, 202L, "S0", 0, 5_000L, body,
				body == null ? List.of(77L) : List.of());

		DirectionPost post = DirectionPost.submit(11L, 101L, fingerprint, "submit-key", body, "TEST-REGION",
				SUBMITTED_AT, EXPIRES_AT);

		assertThat(post.getModerationStatus()).isEqualTo(expected);
		assertThat(post.getStatus()).isEqualTo(DirectionPostStatus.MATCHING);
	}

	private static BiFunction<DirectionPost, Instant, Optional<DirectionPost>> passModeration() {
		return DirectionPost::passModeration;
	}

	private static BiFunction<DirectionPost, Instant, Optional<DirectionPost>> rejectModeration() {
		return DirectionPost::rejectModeration;
	}

	private static BiFunction<DirectionPost, Instant, Optional<DirectionPost>> holdForReview() {
		return DirectionPost::holdModerationForReview;
	}

	private static DirectionPost post(DirectionPostStatus status, DirectionPostModerationStatus moderationStatus,
			Instant expiresAt) {
		DirectionRequestFingerprint fingerprint = DirectionRequestFingerprint.create(101L, 202L, "S0", 0, 5_000L,
				"moderation body");
		return DirectionPost.restore(1L, 11L, 101L, fingerprint, status, "moderation-key", "moderation body",
				"TEST-REGION", moderationStatus, SUBMITTED_AT, null, expiresAt, null,
				status == DirectionPostStatus.DELETED ? NOW : null);
	}
}
