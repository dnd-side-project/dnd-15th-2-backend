package com.dnd.qello.safety.web.response;

import java.util.List;

import com.dnd.qello.safety.domain.ReportReason;
import com.dnd.qello.safety.domain.ReportSubReason;

public record ReportReasonResponse(String code, String label, List<String> subReasons, boolean detailRequired) {

	private static final List<ReportReasonResponse> CATALOG = List.of(
		of(ReportReason.SEXUAL_CONTENT, "성적 또는 노골적인 컨텐츠", ReportSubReason.CSAM, ReportSubReason.NCII),
		of(ReportReason.VIOLENCE_OR_THREAT, "폭력, 위협", ReportSubReason.CREDIBLE_THREAT),
		of(ReportReason.HATE_OR_HARASSMENT, "혐오, 괴롭힘"),
		of(ReportReason.PRIVACY_VIOLATION, "개인정보 유출"),
		of(ReportReason.SPAM_OR_ADVERTISING, "스팸, 광고"),
		of(ReportReason.IMPERSONATION, "사칭"),
		of(ReportReason.ILLEGAL_OR_DANGEROUS, "불법 거래 또는 위험 행동"),
		of(ReportReason.OTHER, "기타"));

	public static List<ReportReasonResponse> catalog() {
		return CATALOG;
	}

	private static ReportReasonResponse of(ReportReason reason, String label, ReportSubReason... subReasons) {
		return new ReportReasonResponse(reason.name(), label,
			List.of(subReasons).stream().map(Enum::name).toList(), reason == ReportReason.OTHER);
	}
}
