package com.dnd.qello.safety.domain;

// 신고 사유 8종. DB `ck_report_reason` CHECK와 이름이 정확히 일치해야 한다.
public enum ReportReason {
	SEXUAL_CONTENT,
	VIOLENCE_OR_THREAT,
	HATE_OR_HARASSMENT,
	PRIVACY_VIOLATION,
	SPAM_OR_ADVERTISING,
	IMPERSONATION,
	ILLEGAL_OR_DANGEROUS,
	OTHER
}
