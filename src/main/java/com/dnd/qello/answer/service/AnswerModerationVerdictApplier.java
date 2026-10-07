package com.dnd.qello.answer.service;

import java.time.Instant;

import org.springframework.stereotype.Component;

import com.dnd.qello.answer.error.AnswerErrorCode;
import com.dnd.qello.answer.error.AnswerException;
import com.dnd.qello.filtering.domain.FilterTargetType;
import com.dnd.qello.filtering.domain.FilterVerdict;
import com.dnd.qello.filtering.moderation.ModerationVerdictApplier;

import lombok.RequiredArgsConstructor;

/**
 * 답변 moderation 판정을 답변 공개 여부에 반영한다(#137에서 AnswerModerationVerdictWorker에서 분리).
 *
 * <p>
 * ALLOW는 {@link AnswerNotificationService#publish}에, BLOCK은
 * {@link AnswerNotificationService#reject}에 위임한다. 두 메서드 모두 이미 종결
 * 상태(PUBLISHED/REJECTED)면 멱등하게 반환하므로 이 클래스는 자체 중복 방지 로직을 두지 않는다.
 * </p>
 *
 * <p>
 * 이 클래스는 트랜잭션을 열지 않는다. publish()·reject()가 각자 자신의 트랜잭션을 소유해야, publish()가 정책상
 * 예상된 INVALID_ANSWER_STATUS로 실패했을 때 그 rollback이 호출자 트랜잭션을 rollback-only로 만들지
 * 않는다. 호출자 트랜잭션에 묶이면 이어지는 claim 완료 commit이 UnexpectedRollbackException으로 실패하고,
 * 이벤트가 lease 만료마다 무한 재처리된다.
 * </p>
 */
@Component
@RequiredArgsConstructor
public class AnswerModerationVerdictApplier implements ModerationVerdictApplier {

	private final AnswerNotificationService answerNotificationService;

	@Override
	public FilterTargetType targetType() {
		return FilterTargetType.ANSWER;
	}

	@Override
	public void applyVerdict(long targetId, FilterVerdict verdict, Instant at) {
		if (verdict == FilterVerdict.ALLOW) {
			publish(targetId, at);
		} else {
			answerNotificationService.reject(targetId, at);
		}
	}

	// fail-closed로 답변을 건드리지 않는다(INV-ANS-003, INV-ANS-004). 늦게 도착한 유효 ALLOW/BLOCK도
	// 그대로 적용할 수 있어야 하므로 이 시점에 답변을 종결 상태로 만들지 않는다.
	@Override
	public void applyDeadlineElapsed(long targetId, Instant at) {
	}

	// releaseSlot 실패(post_recipient가 이미 EXPIRED/BLOCKED/SKIPPED로 선점됨)는 재시도로 해소되지 않는
	// 정책 결과다. 이벤트를 완료 처리하고 답변은 공개되지 않은 채로 둔다.
	private void publish(long answerId, Instant at) {
		try {
			answerNotificationService.publish(answerId, at);
		} catch (AnswerException ineligible) {
			if (ineligible.getErrorCode() != AnswerErrorCode.INVALID_ANSWER_STATUS) {
				throw ineligible;
			}
		}
	}
}
