/**
 * Created at: 2026-10-05T18:10:06+09:00
 * Source scenario: TEST-PLAN-GH-310-PROPOSAL-DELETE-MUTE-UNIT-001 through UNIT-007
 */
package com.dnd.qello.question.domain;

import java.time.Instant;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import com.dnd.qello.question.error.QuestionErrorCode;
import com.dnd.qello.question.error.QuestionException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class QuestionProposalDeleteMuteTest {

	private static final Instant SUBMITTED_AT = Instant.parse("2026-10-01T00:00:00Z");
	private static final Instant DELETED_AT = Instant.parse("2026-10-05T00:00:00Z");

	@ParameterizedTest
	@EnumSource(value = QuestionProposalStatus.class, names = {"SUBMITTED", "UNDER_REVIEW", "APPROVED", "REJECTED"})
	@DisplayName("제출 이후 모든 상태의 제안은 삭제 시각만 기록되고 상태는 그대로다")
	void deleteRecordsTimeWithoutChangingStatus(QuestionProposalStatus status) {
		QuestionProposal proposal = proposal(status, null, false);

		QuestionProposal deleted = proposal.delete(DELETED_AT);

		assertThat(deleted.isDeleted()).isTrue();
		assertThat(deleted.getDeletedAt()).isEqualTo(DELETED_AT);
		assertThat(deleted.getStatus()).isEqualTo(status);
		assertThat(deleted.getProposedText()).isEqualTo(proposal.getProposedText());
	}

	@Test
	@DisplayName("삭제 시각이 없으면 REQUIRED_VALUE_MISSING으로 거부한다")
	void deleteRequiresTime() {
		QuestionProposal proposal = proposal(QuestionProposalStatus.SUBMITTED, null, false);

		assertThatThrownBy(() -> proposal.delete(null))
				.isInstanceOf(QuestionException.class)
				.satisfies(exception -> assertThat(((QuestionException) exception).getErrorCode())
						.isEqualTo(QuestionErrorCode.REQUIRED_VALUE_MISSING));
	}

	@Test
	@DisplayName("이미 삭제한 제안을 다시 삭제하면 최초 삭제 시각을 유지한다")
	void deleteIsIdempotent() {
		QuestionProposal deleted = proposal(QuestionProposalStatus.APPROVED, DELETED_AT, false);

		QuestionProposal again = deleted.delete(DELETED_AT.plusSeconds(60));

		assertThat(again.getDeletedAt()).isEqualTo(DELETED_AT);
	}

	@Test
	@DisplayName("삭제한 UNDER_REVIEW 제안은 검수 시작·승인·반려 모두 INVALID_PROPOSAL_STATUS로 거부한다")
	void deletedProposalRejectsReviewTransitions() {
		QuestionProposal deletedUnderReview = proposal(QuestionProposalStatus.UNDER_REVIEW, DELETED_AT, false);
		QuestionProposal deletedSubmitted = proposal(QuestionProposalStatus.SUBMITTED, DELETED_AT, false);

		assertInvalidStatus(() -> deletedSubmitted.startReview());
		assertInvalidStatus(() -> deletedUnderReview.approve(null));
		assertInvalidStatus(() -> deletedUnderReview.reject("부적절한 문구"));
	}

	@Test
	@DisplayName("알림을 끈 뒤 다시 켜면 muted 값만 바뀌고 다른 값은 유지된다")
	void changeNotificationMutedTogglesOnlyFlag() {
		QuestionProposal proposal = proposal(QuestionProposalStatus.UNDER_REVIEW, null, false);

		QuestionProposal muted = proposal.changeNotificationMuted(true);
		QuestionProposal unmuted = muted.changeNotificationMuted(false);

		assertThat(muted.isNotificationMuted()).isTrue();
		assertThat(muted.isPushMuted()).isTrue();
		assertThat(unmuted.isNotificationMuted()).isFalse();
		assertThat(unmuted.isPushMuted()).isFalse();
		assertThat(unmuted.getStatus()).isEqualTo(QuestionProposalStatus.UNDER_REVIEW);
		assertThat(unmuted.getSubmittedAt()).isEqualTo(SUBMITTED_AT);
		assertThat(unmuted.isDeleted()).isFalse();
	}

	@Test
	@DisplayName("이미 알림을 끈 제안을 다시 끄면 muted가 그대로 true다")
	void changeNotificationMutedIsIdempotent() {
		QuestionProposal muted = proposal(QuestionProposalStatus.SUBMITTED, null, true);

		assertThat(muted.changeNotificationMuted(true).isNotificationMuted()).isTrue();
	}

	@Test
	@DisplayName("삭제한 제안의 알림 설정 변경은 PROPOSAL_NOT_FOUND로 거부한다")
	void deletedProposalRejectsNotificationChange() {
		QuestionProposal deleted = proposal(QuestionProposalStatus.APPROVED, DELETED_AT, false);

		assertThatThrownBy(() -> deleted.changeNotificationMuted(true))
				.isInstanceOf(QuestionException.class)
				.satisfies(exception -> assertThat(((QuestionException) exception).getErrorCode())
						.isEqualTo(QuestionErrorCode.PROPOSAL_NOT_FOUND));
	}

	@Test
	@DisplayName("알림을 켜 둔 제안도 삭제하면 push를 받지 않는다")
	void deletedProposalIsPushMuted() {
		QuestionProposal deleted = proposal(QuestionProposalStatus.APPROVED, DELETED_AT, false);

		assertThat(deleted.isNotificationMuted()).isFalse();
		assertThat(deleted.isPushMuted()).isTrue();
	}

	private static void assertInvalidStatus(org.assertj.core.api.ThrowableAssert.ThrowingCallable call) {
		assertThatThrownBy(call)
				.isInstanceOf(QuestionException.class)
				.satisfies(exception -> assertThat(((QuestionException) exception).getErrorCode())
						.isEqualTo(QuestionErrorCode.INVALID_PROPOSAL_STATUS));
	}

	private static QuestionProposal proposal(QuestionProposalStatus status, Instant deletedAt, boolean muted) {
		String reason = status == QuestionProposalStatus.REJECTED ? "반려 사유" : null;
		return QuestionProposal.restore(7L, 5L, status, "제안 문구", reason,
				SUBMITTED_AT, SUBMITTED_AT, SUBMITTED_AT, deletedAt, muted);
	}
}
