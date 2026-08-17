/**
 * Created at: 2026-08-17T16:55:00+09:00
 * Source scenario: TEST-PLAN-GH-125-ANSWER-SUBMISSION-PUBLICATION-API-UNIT-019
 */
package com.dnd.qello.answer.web;

import static org.assertj.core.api.Assertions.assertThat;

import java.lang.reflect.RecordComponent;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.Arrays;
import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.dnd.qello.answer.domain.Answer;
import com.dnd.qello.answer.web.response.AnswerSubmissionResponse;

class AnswerSubmissionWebContractTest {

	@Test
	@DisplayName("UNIT-019: 답변 제출 API는 ApiSpec과 Controller를 분리한다")
	void keepsApiBoundaryTypesSeparated() {
		assertThat(AnswerSubmissionApiSpec.class.isAssignableFrom(AnswerSubmissionController.class)).isTrue();
	}

	@Test
	@DisplayName("UNIT-019: 응답 DTO는 answerId·submissionStatus·submittedAt만 노출하고 좌표·내부 사용자 ID·본문은 없다")
	void responseExposesOnlyNonSensitiveFields() {
		assertThat(recordComponentNames(AnswerSubmissionResponse.class))
			.containsExactly("answerId", "submissionStatus", "submittedAt");
		assertThat(recordComponentNames(AnswerSubmissionResponse.class)).noneMatch(name -> {
			String lower = name.toLowerCase();
			return lower.contains("body") || lower.contains("author") || lower.contains("latitude")
				|| lower.contains("longitude") || lower.contains("bearing") || lower.contains("region")
				|| lower.contains("distance") || lower.contains("recipient");
		});
	}

	@Test
	@DisplayName("UNIT-019: from()은 Answer 도메인 값만 매핑하고 별도 필드를 계산해 추가하지 않는다")
	void fromMapsOnlyDomainFields() {
		Answer answer = Answer.submit(1L, 11L, "key", "본문", "TEST", BigDecimal.valueOf(90), "NEAR",
			Instant.parse("2026-08-17T00:00:00Z"), 5000L);
		answer = Answer.restore(200L, answer.getPostRecipientId(), answer.getAuthorId(), answer.getStatus(),
			answer.getIdempotencyKey(), answer.getBodyText(), answer.getCoarseRegionCode(),
			answer.getBearingFromSenderDegrees(), answer.getDistanceBand(), answer.getModerationStatus(),
			answer.getSubmittedAt(), null, null, answer.getDistanceM(), null, 0);

		AnswerSubmissionResponse response = AnswerSubmissionResponse.from(answer);

		assertThat(response.answerId()).isEqualTo(200L);
		assertThat(response.submissionStatus()).isEqualTo("SUBMITTED");
		assertThat(response.submittedAt()).isEqualTo(Instant.parse("2026-08-17T00:00:00Z"));
	}

	private static List<String> recordComponentNames(Class<?> type) {
		return Arrays.stream(type.getRecordComponents()).map(RecordComponent::getName).toList();
	}
}
