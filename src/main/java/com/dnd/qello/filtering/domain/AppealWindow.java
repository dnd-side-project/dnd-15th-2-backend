package com.dnd.qello.filtering.domain;

import java.time.Duration;
import java.time.Instant;

import com.dnd.qello.filtering.error.FilteringErrorCode;
import com.dnd.qello.filtering.error.FilteringException;

// 이의제기 접수 기간의 순수 평가. 전 세계 공통 기간이라 지역별 분기가 없다.
//
// 접수 기간을 설정이나 요청 파라미터로 주입받지 않는 이유: 주입 경로가 있으면
// 그 경로 자체가 "기간을 6개월보다 줄이는 경로"가 되어 INV-APL-008, INV-APL-009를
// 위반한다. 생성자가 하한 미만을 거절하므로 더 긴 기간만 만들 수 있다.
public record AppealWindow(Duration acceptanceWindow) {

	// 6개월의 일 단위 환산값. 이슈 #112가 calendar-month·timezone 계산을
	// 미결정으로 제외했으므로 Period를 쓰지 않는다.
	//
	// 184일을 고른 근거: 어떤 6개 달력월 구간도 최대 184일(7·8·10·12월이 모두
	// 낀 구간)이다. 따라서 184일은 어떤 기산점에서도 6개월보다 짧아지지 않는다.
	// 하한을 지키는 방향으로만 반올림한 값이다.
	public static final Duration GLOBAL_ACCEPTANCE_WINDOW = Duration.ofDays(184);

	public static final AppealWindow GLOBAL = new AppealWindow(GLOBAL_ACCEPTANCE_WINDOW);

	public AppealWindow {
		if (acceptanceWindow == null) {
			throw new FilteringException(FilteringErrorCode.REQUIRED_VALUE_MISSING, "acceptanceWindow");
		}
		if (acceptanceWindow.compareTo(GLOBAL_ACCEPTANCE_WINDOW) < 0) {
			throw new FilteringException(FilteringErrorCode.INVALID_VALUE_RANGE, "acceptanceWindow",
				"접수 기간은 6개월보다 짧을 수 없습니다");
		}
	}

	// 접수 가능 여부를 판정한다. 기산점을 신뢰할 수 없으면 거절하지 않고 접수
	// 시각을 기산점으로 삼아 접수를 허용한다(이슈 #112의 fallback) — 판정 시각
	// 데이터의 결함이 곧 작성자의 구제 거부가 되지 않게 한다.
	public AppealAcceptance evaluate(Instant windowStartedAt, Instant now) {
		if (now == null) {
			throw new FilteringException(FilteringErrorCode.REQUIRED_VALUE_MISSING, "now");
		}
		if (windowStartedAt == null || windowStartedAt.isAfter(now)) {
			return new AppealAcceptance(true, AppealAcceptanceReasonCode.WINDOW_UNVERIFIABLE, now);
		}
		if (now.isAfter(windowStartedAt.plus(acceptanceWindow))) {
			return new AppealAcceptance(false, AppealAcceptanceReasonCode.WINDOW_ELAPSED, windowStartedAt);
		}
		return new AppealAcceptance(true, AppealAcceptanceReasonCode.WITHIN_WINDOW, windowStartedAt);
	}

	public Instant expiresAt(Instant windowStartedAt) {
		if (windowStartedAt == null) {
			throw new FilteringException(FilteringErrorCode.REQUIRED_VALUE_MISSING, "windowStartedAt");
		}
		return windowStartedAt.plus(acceptanceWindow);
	}
}
