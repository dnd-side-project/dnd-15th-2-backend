package com.dnd.qello.safety.web.response;

import static org.assertj.core.api.Assertions.assertThat;

import java.lang.reflect.RecordComponent;
import java.util.Arrays;
import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Created at: 2026-08-18T22:15:00+09:00
 * Source scenario: TEST-PLAN-GH-154-REPORT-INTAKE-API-UNIT-009 through UNIT-012
 */
class ReportResponseContractTest {

	@Test
	@DisplayName("사유 카탈로그 응답은 code·label·subReasons·detailRequired 4개 필드만 갖는다")
	void reasonResponseHasExactlyFourFields() {
		assertThat(recordComponentNames(ReportReasonResponse.class))
			.containsExactly("code", "label", "subReasons", "detailRequired");
	}

	@Test
	@DisplayName("OTHER만 detailRequired가 true이고 나머지 7종은 false다")
	void onlyOtherRequiresDetail() {
		List<ReportReasonResponse> catalog = ReportReasonResponse.catalog();

		assertThat(catalog).hasSize(8);
		assertThat(catalog.stream().filter(ReportReasonResponse::detailRequired))
			.extracting(ReportReasonResponse::code).containsExactly("OTHER");
		assertThat(catalog.stream().filter(r -> r.code().equals("SEXUAL_CONTENT")).findFirst().orElseThrow().subReasons())
			.containsExactlyInAnyOrder("CSAM", "NCII");
	}

	@Test
	@DisplayName("접수증 응답은 reportId·status·receivedAt·alreadyReceived·guidance 5개 필드만 갖는다 (INV-RPT-005)")
	void receiptResponseHasExactlyFiveFields() {
		assertThat(recordComponentNames(ReportReceiptResponse.class))
			.containsExactly("reportId", "status", "receivedAt", "alreadyReceived", "guidance");
	}

	@Test
	@DisplayName("목록·상세 응답에는 상대 식별자나 내부 판단 필드가 없다 (INV-RPT-005)")
	void summaryAndDetailResponsesExposeNoInternalFields() {
		List<String> summaryFields = recordComponentNames(ReportSummaryResponse.class);
		List<String> detailFields = recordComponentNames(ReportDetailResponse.class);

		assertThat(summaryFields).containsExactly("reportId", "reasonCode", "status", "createdAt");
		assertThat(detailFields).containsExactly(
			"reportId", "reasonCode", "subReasonCode", "detail", "status", "createdAt", "resolvedAt");
		assertThat(summaryFields).noneMatch(ReportResponseContractTest::looksLikeInternalOrTargetField);
		assertThat(detailFields).noneMatch(ReportResponseContractTest::looksLikeInternalOrTargetField);
	}

	private static boolean looksLikeInternalOrTargetField(String name) {
		String lower = name.toLowerCase();
		return lower.contains("internal") || lower.contains("reviewer") || lower.contains("author")
			|| lower.contains("targetuser") || lower.contains("answerid") || lower.contains("caseid")
			|| lower.contains("directionpost");
	}

	private static List<String> recordComponentNames(Class<?> type) {
		return Arrays.stream(type.getRecordComponents()).map(RecordComponent::getName).toList();
	}
}
