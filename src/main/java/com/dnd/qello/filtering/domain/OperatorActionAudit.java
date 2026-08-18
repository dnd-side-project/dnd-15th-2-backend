package com.dnd.qello.filtering.domain;

import java.time.Instant;

import com.dnd.qello.filtering.error.FilteringErrorCode;
import com.dnd.qello.filtering.error.FilteringException;

// 운영자가 필터링 authority를 바꾼 사실 하나. INV-APL-012가 요구하는 네 요소
// (actor, reason, policy version, 시간)를 모두 강제한다.
//
// 이 객체에는 상태 전이 메서드가 없다. 감사 기록은 만들어진 뒤 바뀌지 않는다.
public record OperatorActionAudit(
	Long id, long operatorUserId, OperatorActionType actionType, OperatorActionTargetType targetType,
	String targetKey, String reasonCode, String reasonText, String policyVersion, Instant occurredAt
) {

	private static final int TARGET_KEY_MAX_LENGTH = 200;
	private static final int REASON_CODE_MAX_LENGTH = 30;
	private static final int REASON_TEXT_MAX_LENGTH = 500;
	private static final int POLICY_VERSION_MAX_LENGTH = 50;

	// 관련 정책이 정의돼 있지 않은 행위가 쓰는 값. 정책이 생기면 그 식별자로
	// 교체한다 — 빈 값을 허용하면 "정책 버전이 없는 감사"가 섞인다.
	public static final String UNVERSIONED_POLICY = "unversioned";

	public OperatorActionAudit {
		if (id != null && id <= 0) {
			throw new FilteringException(FilteringErrorCode.INVALID_VALUE_RANGE, "id", "id는 양수여야 합니다");
		}
		if (operatorUserId <= 0) {
			throw new FilteringException(
				FilteringErrorCode.INVALID_VALUE_RANGE, "operatorUserId", "operatorUserId는 양수여야 합니다");
		}
		if (actionType == null) {
			throw new FilteringException(FilteringErrorCode.REQUIRED_VALUE_MISSING, "actionType");
		}
		if (targetType == null) {
			throw new FilteringException(FilteringErrorCode.REQUIRED_VALUE_MISSING, "targetType");
		}
		requireText(targetKey, "targetKey", TARGET_KEY_MAX_LENGTH);
		requireText(reasonCode, "reasonCode", REASON_CODE_MAX_LENGTH);
		// 공백뿐인 근거를 막는다. 형식만 채운 값을 허용하면 감사 이력에 "왜"가
		// 없는 것과 결과가 같다.
		requireText(reasonText, "reasonText", REASON_TEXT_MAX_LENGTH);
		requireText(policyVersion, "policyVersion", POLICY_VERSION_MAX_LENGTH);
		if (occurredAt == null) {
			throw new FilteringException(FilteringErrorCode.REQUIRED_VALUE_MISSING, "occurredAt");
		}
	}

	public static OperatorActionAudit record(long operatorUserId, OperatorActionType actionType,
		OperatorActionTargetType targetType, String targetKey, String reasonCode, String reasonText,
		String policyVersion, Instant now) {
		return new OperatorActionAudit(null, operatorUserId, actionType, targetType, targetKey, reasonCode,
			reasonText, policyVersion, now);
	}

	public static OperatorActionAudit restore(Long id, long operatorUserId, OperatorActionType actionType,
		OperatorActionTargetType targetType, String targetKey, String reasonCode, String reasonText,
		String policyVersion, Instant occurredAt) {
		return new OperatorActionAudit(id, operatorUserId, actionType, targetType, targetKey, reasonCode,
			reasonText, policyVersion, occurredAt);
	}

	private static void requireText(String value, String field, int maxLength) {
		if (value == null || value.isBlank() || value.length() > maxLength) {
			throw new FilteringException(FilteringErrorCode.INVALID_TEXT, field, field + " 값이 유효하지 않습니다");
		}
	}
}
