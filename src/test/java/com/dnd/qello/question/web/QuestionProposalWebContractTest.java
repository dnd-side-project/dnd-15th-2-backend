/**
 * Created at: 2026-08-16T00:30:00+09:00
 * Source scenario: TEST-PLAN-GH-144-QUESTION-PROPOSAL-API-UNIT-008, UNIT-009
 * (임시 식별자 — /harness-test-plan 승인 전까지 이 시나리오 번호만 사용)
 */
package com.dnd.qello.question.web;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.dnd.qello.question.web.request.ApproveQuestionProposalRequest;
import com.dnd.qello.question.web.request.RejectQuestionProposalRequest;
import com.dnd.qello.question.web.request.SubmitQuestionProposalRequest;
import com.dnd.qello.question.web.response.ApprovedQuestionResponse;
import com.dnd.qello.question.web.response.QuestionProposalResponse;
import com.dnd.qello.question.web.response.QuestionProposalReviewResponse;

class QuestionProposalWebContractTest {

	@Test
	@DisplayName("사용자용 API는 ApiSpec과 Controller를 분리한다")
	void keepsUserApiBoundaryTypesSeparated() {
		assertThat(QuestionProposalApiSpec.class.isAssignableFrom(QuestionProposalController.class)).isTrue();
		assertThat(SubmitQuestionProposalRequest.class.getPackageName())
			.isEqualTo("com.dnd.qello.question.web.request");
		assertThat(QuestionProposalResponse.class.getPackageName())
			.isEqualTo("com.dnd.qello.question.web.response");
	}

	@Test
	@DisplayName("운영자용 API는 ApiSpec과 Controller를 분리한다")
	void keepsOperatorApiBoundaryTypesSeparated() {
		assertThat(OperatorQuestionProposalApiSpec.class.isAssignableFrom(OperatorQuestionProposalController.class))
			.isTrue();
		assertThat(ApproveQuestionProposalRequest.class.getPackageName())
			.isEqualTo("com.dnd.qello.question.web.request");
		assertThat(RejectQuestionProposalRequest.class.getPackageName())
			.isEqualTo("com.dnd.qello.question.web.request");
		assertThat(ApprovedQuestionResponse.class.getPackageName())
			.isEqualTo("com.dnd.qello.question.web.response");
		assertThat(QuestionProposalReviewResponse.class.getPackageName())
			.isEqualTo("com.dnd.qello.question.web.response");
	}

	@Test
	@DisplayName("사용자 경로는 /api/v1/questions, 운영자 경로는 /admin/questions 아래에 있다")
	void separatesUserAndOperatorBasePaths() {
		var userMapping = QuestionProposalController.class.getAnnotation(
			org.springframework.web.bind.annotation.RequestMapping.class);
		var operatorMapping = OperatorQuestionProposalController.class.getAnnotation(
			org.springframework.web.bind.annotation.RequestMapping.class);

		assertThat(userMapping.value()).containsExactly("/api/v1/questions");
		assertThat(operatorMapping.value()).containsExactly("/admin/questions");
	}
}
