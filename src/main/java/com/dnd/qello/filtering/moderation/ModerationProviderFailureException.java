package com.dnd.qello.filtering.moderation;

import com.dnd.qello.filtering.domain.ModerationFailureClassification;
import com.dnd.qello.filtering.error.FilteringErrorCode;
import com.dnd.qello.filtering.error.FilteringException;

// moderation 공급자 호출 실패에 원인 분류(#109)를 함께 실어 나르는
// FilteringException(MODERATION_PROVIDER_UNAVAILABLE) 하위 타입. 기존 호출자
// (AnswerModerationExecutionWorker 등)는 이 타입을 몰라도 기존 FilteringException
// 계약만으로 그대로 동작한다 — classification은 synthetic snapshot health probe처럼
// 원인 분류가 필요한 새 호출자만 꺼내 쓴다.
public class ModerationProviderFailureException extends FilteringException {

	private final ModerationFailureClassification classification;

	public ModerationProviderFailureException(
		ModerationFailureClassification classification, String field, String reason
	) {
		super(FilteringErrorCode.MODERATION_PROVIDER_UNAVAILABLE, field, reason);
		this.classification = requireClassification(classification);
	}

	public ModerationProviderFailureException(
		ModerationFailureClassification classification, String field, String reason, Throwable cause
	) {
		super(FilteringErrorCode.MODERATION_PROVIDER_UNAVAILABLE, field, reason, cause);
		this.classification = requireClassification(classification);
	}

	public ModerationFailureClassification classification() {
		return classification;
	}

	private static ModerationFailureClassification requireClassification(ModerationFailureClassification value) {
		if (value == null) {
			throw new FilteringException(FilteringErrorCode.REQUIRED_VALUE_MISSING, "classification");
		}
		return value;
	}
}
