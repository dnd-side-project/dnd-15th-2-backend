package com.dnd.qello.safety.domain;

public enum ReportCaseSeverity {
	NORMAL,
	CRITICAL;

	/**
	 * CSAM·NCII·CREDIBLE_THREAT·SELF_HARM_RISK는 CRITICAL, 그 외(subReason
	 * 없음 포함)는 NORMAL(#156, SELF_HARM_RISK는 #157).
	 */
	public static ReportCaseSeverity of(ReportSubReason subReason) {
		if (subReason == null) {
			return NORMAL;
		}
		return switch (subReason) {
			case CSAM, NCII, CREDIBLE_THREAT, SELF_HARM_RISK -> CRITICAL;
		};
	}
}
