package com.dnd.qello.common.error;

import org.springframework.stereotype.Component;

import com.dnd.qello.account.error.AccountErrorCode;
import com.dnd.qello.answer.error.AnswerErrorCode;
import com.dnd.qello.auth.error.AuthErrorCode;
import com.dnd.qello.direction.error.DirectionErrorCode;
import com.dnd.qello.notification.error.NotificationErrorCode;
import com.dnd.qello.question.error.QuestionErrorCode;
import com.dnd.qello.safety.error.SafetyErrorCode;

// DB 제약(유일성, 외래키, constraint trigger) 이름을 기능 오류 코드로 옮기는 매퍼.
//
// 제약 이름은 Flyway 마이그레이션이 정의한 값. 마이그레이션에서 제약 이름 변경 시
// 이 매핑도 함께 수정 필요. 매핑되지 않은 제약은 CommonErrorCode.CONFLICT로 처리.
@Component
public class ConstraintExceptionMapper {

	public ConstraintMapping map(String constraintName) {
		if (constraintName == null || constraintName.isBlank()) {
			return new ConstraintMapping(CommonErrorCode.CONFLICT, null);
		}

		return switch (constraintName) {
			case "uq_direction_post_idempotency" ->
				new ConstraintMapping(DirectionErrorCode.DUPLICATED_POST, "idempotencyKey");
			case "uq_post_recipient_post_user" ->
				new ConstraintMapping(DirectionErrorCode.DUPLICATED_RECIPIENT, "recipientId");
			case "fk_post_reaction_recipient" ->
				new ConstraintMapping(DirectionErrorCode.INELIGIBLE_REACTOR, "reactorId");
			case "uq_answer_idempotency" ->
				new ConstraintMapping(AnswerErrorCode.DUPLICATED_ANSWER, "idempotencyKey");
			case "uq_answer_one_per_recipient" ->
				new ConstraintMapping(AnswerErrorCode.DUPLICATE_ACTIVE_ANSWER, "postRecipientId");
			case "ct_answer_reaction_reactor_is_sender" ->
				new ConstraintMapping(AnswerErrorCode.INELIGIBLE_REACTOR, "reactorId");
			case "uq_notification_recipient_dedup", "uq_outbox_event_dedup" ->
				new ConstraintMapping(NotificationErrorCode.DUPLICATED_EVENT, "dedupKey");
			case "uq_question_assignment_cycle_question", "uq_question_assignment_cycle_order" ->
				new ConstraintMapping(QuestionErrorCode.DUPLICATED_ASSIGNMENT, "approvedQuestionId");
			case "uq_open_report_user", "uq_open_report_post", "uq_open_report_answer" ->
				new ConstraintMapping(SafetyErrorCode.DUPLICATED_OPEN_REPORT, null);
			// 체크-후-삽입 사이의 경합 창에서만 도달한다. DeviceRegistrationService가
			// 먼저 조회로 같은 상황을 막지만, 동시 요청 race는 이 매핑이 최종 방어선이다.
			case "uq_active_device_installation" ->
				new ConstraintMapping(AuthErrorCode.DEVICE_ALREADY_REGISTERED, "installationId");
			case "media_attachment_pkey" ->
				new ConstraintMapping(AnswerErrorCode.INVALID_MEDIA_STATUS, "mediaId");
			case "uq_user_account_nickname_ci" ->
				new ConstraintMapping(AccountErrorCode.DUPLICATED_NICKNAME, "nickname");
			default -> new ConstraintMapping(CommonErrorCode.CONFLICT, null);
		};
	}

	// 매핑에 사용하는 제약 이름 목록. 예외 메시지에서 이 이름을 찾아 제약 식별
	public String[] knownConstraints() {
		return new String[] {
			"uq_direction_post_idempotency",
			"uq_post_recipient_post_user",
			"fk_post_reaction_recipient",
			"uq_answer_idempotency",
			"uq_answer_one_per_recipient",
			"ct_answer_reaction_reactor_is_sender",
			"uq_notification_recipient_dedup",
			"uq_outbox_event_dedup",
			"uq_question_assignment_cycle_question",
			"uq_question_assignment_cycle_order",
			"uq_open_report_user",
			"uq_open_report_post",
			"uq_open_report_answer",
			"uq_active_device_installation",
			"media_attachment_pkey",
			"uq_user_account_nickname_ci"
		};
	}
}
