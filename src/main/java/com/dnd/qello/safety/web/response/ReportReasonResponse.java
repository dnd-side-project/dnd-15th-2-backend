package com.dnd.qello.safety.web.response;

import java.util.List;

import com.dnd.qello.safety.domain.ReportReason;
import com.dnd.qello.safety.domain.ReportSubReason;

import io.swagger.v3.oas.annotations.media.Schema;

public record ReportReasonResponse(
	@Schema(description = "신고 사유를 요청에 전달할 코드.") String code,
	@Schema(description = "화면에 표시할 신고 사유 이름.") String label,
	@Schema(description = "이 사유를 선택했을 때 고를 수 있는 하위 사유 코드 목록.") List<String> subReasons,
	@Schema(description = "추가 설명을 입력해야 하는지 여부.") boolean detailRequired) {

	private static final List<ReportReasonResponse> CATALOG = List.of(
		of(ReportReason.SEXUAL_CONTENT, "성적 또는 노골적인 컨텐츠", ReportSubReason.CSAM, ReportSubReason.NCII),
		of(ReportReason.VIOLENCE_OR_THREAT, "폭력, 위협", ReportSubReason.CREDIBLE_THREAT),
		of(ReportReason.HATE_OR_HARASSMENT, "혐오, 괴롭힘"),
		of(ReportReason.PRIVACY_VIOLATION, "개인정보 유출"),
		of(ReportReason.SPAM_OR_ADVERTISING, "스팸, 광고"),
		of(ReportReason.IMPERSONATION, "사칭"),
		of(ReportReason.ILLEGAL_OR_DANGEROUS, "불법 거래 또는 위험 행동", ReportSubReason.SELF_HARM_RISK),
		of(ReportReason.OTHER, "기타"));

	public static List<ReportReasonResponse> catalog() {
		return CATALOG;
	}

	private static ReportReasonResponse of(ReportReason reason, String label, ReportSubReason... subReasons) {
		return new ReportReasonResponse(reason.name(), label,
			List.of(subReasons).stream().map(Enum::name).toList(), reason == ReportReason.OTHER);
	}
}
