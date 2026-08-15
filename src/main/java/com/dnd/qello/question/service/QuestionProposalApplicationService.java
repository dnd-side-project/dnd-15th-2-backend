package com.dnd.qello.question.service;

import java.time.Clock;
import java.util.List;

import org.springframework.stereotype.Service;

import com.dnd.qello.account.domain.Account;
import com.dnd.qello.account.domain.AccountRole;
import com.dnd.qello.account.domain.AccountStatus;
import com.dnd.qello.account.repository.AccountRepository;
import com.dnd.qello.question.domain.QuestionProposal;
import com.dnd.qello.question.error.QuestionErrorCode;
import com.dnd.qello.question.error.QuestionException;
import com.dnd.qello.question.repository.QuestionProposalRepository;

/**
 * 인증 사용자를 질문 제안 도메인 명령으로 변환하는 application facade.
 *
 * <p>web 계층은 계정 자격을 판단하지 않는다. 이 facade가 JWT subject에 해당하는
 * ACTIVE USER 계정을 확인한 뒤 {@link QuestionReviewService}의 단일 transaction에
 * 위임한다.</p>
 */
@Service
public class QuestionProposalApplicationService {

	private final AccountRepository accountRepository;
	private final QuestionProposalRepository proposalRepository;
	private final QuestionReviewService reviewService;
	private final Clock clock;

	public QuestionProposalApplicationService(
		AccountRepository accountRepository,
		QuestionProposalRepository proposalRepository,
		QuestionReviewService reviewService,
		Clock clock
	) {
		this.accountRepository = accountRepository;
		this.proposalRepository = proposalRepository;
		this.reviewService = reviewService;
		this.clock = clock;
	}

	public QuestionProposal submit(long proposerId, String proposedText) {
		ensureActiveUser(proposerId);
		return reviewService.propose(proposerId, proposedText, clock.instant());
	}

	public List<QuestionProposal> findMine(long proposerId) {
		ensureActiveUser(proposerId);
		return proposalRepository.findAllByProposerIdOrderByCreatedAtDesc(proposerId);
	}

	private void ensureActiveUser(long userId) {
		Account account = accountRepository.findById(userId)
			.orElseThrow(() -> new QuestionException(QuestionErrorCode.PROPOSER_ACCOUNT_NOT_FOUND, "proposerId"));
		if (account.getRole() != AccountRole.USER || account.getStatus() != AccountStatus.ACTIVE) {
			throw new QuestionException(QuestionErrorCode.PROPOSER_ACCOUNT_NOT_ELIGIBLE, "proposerId");
		}
	}
}
