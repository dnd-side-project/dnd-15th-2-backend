package com.dnd.qello.safety.domain;

public enum ReportCaseQueue {
	STANDARD,
	URGENT;

	/** CRITICAL은 URGENT, NORMAL은 STANDARD로 라우팅한다(#156). */
	public static ReportCaseQueue of(ReportCaseSeverity severity) {
		return switch (severity) {
			case CRITICAL -> URGENT;
			case NORMAL -> STANDARD;
		};
	}
}
