package com.dnd.qello.filtering.moderation;

import java.time.Instant;

import com.dnd.qello.filtering.domain.FilterTargetType;
import com.dnd.qello.filtering.domain.FilterVerdict;

/**
 * 이미 내려진 moderation 판정을 대상 콘텐츠 상태에 반영하는 대상 종류별 진입점(#137).
 * {@link AnswerModerationVerdictWorker}가 이벤트의 targetType으로 구현체를 골라 호출하므로,
 * filtering은 답변·질문글 같은 대상 모듈의 상태 모델을 알 필요가 없다.
 *
 * <p>
 * 같은 이벤트가 lease 만료 뒤 다시 전달될 수 있고 VERDICT_READY와 DEADLINE_ELAPSED의 처리 순서도 보장되지
 * 않는다. 구현체는 두 메서드를 멱등하게 만들고, 이미 확정된 상태를 되돌리지 않는다. 재시도로 해소되지 않는 정책 결과(대상이 이미 다른
 * 상태로 넘어간 경우 등)는 예외 없이 끝내야 이벤트가 무한 재처리되지 않는다.
 * </p>
 */
public interface ModerationVerdictApplier {

	FilterTargetType targetType();

	void applyVerdict(long targetId, FilterVerdict verdict, Instant at);

	// deadline 신호는 승인을 뜻하지 않는다(INV-ANS-003). 이 신호에 무엇을 할지는 대상 정책이 정한다.
	void applyDeadlineElapsed(long targetId, Instant at);
}
