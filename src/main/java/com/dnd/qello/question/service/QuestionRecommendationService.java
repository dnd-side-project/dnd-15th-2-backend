package com.dnd.qello.question.service;

import java.time.Clock;
import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.dnd.qello.account.domain.Account;
import com.dnd.qello.account.domain.AccountRole;
import com.dnd.qello.account.domain.AccountStatus;
import com.dnd.qello.account.repository.AccountRepository;
import com.dnd.qello.question.domain.ApprovedQuestion;
import com.dnd.qello.question.error.QuestionErrorCode;
import com.dnd.qello.question.error.QuestionException;
import com.dnd.qello.question.repository.ApprovedQuestionRepository;

import lombok.RequiredArgsConstructor;

/**
 * 사용자가 질문 전송에 고를 수 있는 질문 목록을 제공한다.
 *
 * <p>
 * 사용자별 배정 cycle 생성이 아직 없어 지금은 전송 검증과 같은
 * {@link ApprovedQuestionRepository#findAssignableAt} 결과를 그대로 반환한다(GH-301). 배정이
 * 완성되면 현재 cycle의 assignment 기준으로 바꾼다.
 * </p>
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class QuestionRecommendationService {

	private final AccountRepository accountRepository;
	private final ApprovedQuestionRepository approvedQuestionRepository;
	private final Clock clock;

	public List<ApprovedQuestion> findRecommendations(long userId) {
		ensureActiveUser(userId);
		return approvedQuestionRepository.findAssignableAt(clock.instant());
	}

	// QuestionProposalApplicationService와 같은 사용자 API 자격 규칙을 따른다.
	private void ensureActiveUser(long userId) {
		Account account = accountRepository.findById(userId)
				.orElseThrow(() -> new QuestionException(QuestionErrorCode.PROPOSER_ACCOUNT_NOT_FOUND, "userId"));
		if (account.getRole() != AccountRole.USER || account.getStatus() != AccountStatus.ACTIVE) {
			throw new QuestionException(QuestionErrorCode.PROPOSER_ACCOUNT_NOT_ELIGIBLE, "userId");
		}
	}
}
