package com.dnd.qello.safety.service;

import com.dnd.qello.safety.error.SafetyErrorCode;
import com.dnd.qello.safety.error.SafetyException;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * REPORT_RESOLVED outbox payload의 JSON 직렬화 지점과 dedup key 형식을 한 곳으로
 * 모은다(#155). payload에 대상·작성자 식별자를 넣지 않는다 — 알림 응답 계약이
 * 상대 식별자를 노출하지 않는 원칙(#154 INV-RPT-005)과 같다.
 */
final class ReportResolutionEventPayloads {

	private ReportResolutionEventPayloads() {
	}

	@JsonIgnoreProperties(ignoreUnknown = true)
	record ReportResolved(long reportId) {
	}

	static String toJson(ObjectMapper objectMapper, Object payload) {
		try {
			return objectMapper.writeValueAsString(payload);
		} catch (JsonProcessingException e) {
			throw new SafetyException(
				SafetyErrorCode.PAYLOAD_SERIALIZATION_FAILED, "payload", "payload 직렬화에 실패했습니다", e);
		}
	}

	static String reportResolvedDedupKey(long reportId) {
		return "report-resolved:" + reportId;
	}
}
