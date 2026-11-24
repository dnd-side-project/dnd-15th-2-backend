package com.dnd.qello.direction.gate;

import java.util.Locale;
import java.util.Set;

import org.springframework.boot.context.properties.ConfigurationProperties;

// 위치 수집 게이트 설정(#379, 위치기반서비스 점검표 B1).
//
// 신고 수리 참조는 "수리됐다"는 사실이 아니라 그 확인을 추적할 수 있는 값을 받는다.
// boolean으로 두면 누가 무엇을 근거로 켰는지 남지 않는다. 판정 규칙은
// filtering production 게이트와 같지만, 두 게이트의 승인 근거가 달라 규칙을
// 공유하지 않는다(테스트 계획 D5).
@ConfigurationProperties(prefix = "qello.location.collection")
public record LocationCollectionGateProperties(
		boolean enabled,
		String lbsRegistrationReference) {

	// 승인 참조로 인정하지 않는 값. 확인 사실을 기록하는 대신 게이트만 통과하려는 값들이다.
	private static final Set<String> PLACEHOLDER_VALUES = Set.of(
			"true", "false", "yes", "no", "y", "n", "1", "0", "ok", "done", "na", "n/a", "todo", "tbd", "-");

	// 신고 수리 문서 번호나 결재 식별자라면 이 길이는 넘는다. 짧은 임의 문자열을 거르는 최소 기준이다.
	private static final int MINIMUM_REFERENCE_LENGTH = 8;

	/** 신고 수리 참조가 추적 가능한 값이면 true. placeholder와 짧은 값은 미확인으로 본다. */
	public boolean hasTraceableRegistrationReference() {
		if (lbsRegistrationReference == null || lbsRegistrationReference.isBlank()) {
			return false;
		}
		String normalized = lbsRegistrationReference.trim().toLowerCase(Locale.ROOT);
		if (PLACEHOLDER_VALUES.contains(normalized)) {
			return false;
		}
		return normalized.length() >= MINIMUM_REFERENCE_LENGTH;
	}
}
