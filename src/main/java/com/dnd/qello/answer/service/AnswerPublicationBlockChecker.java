package com.dnd.qello.answer.service;

import java.util.Optional;

import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import com.dnd.qello.account.domain.Account;
import com.dnd.qello.account.domain.AccountStatus;
import com.dnd.qello.account.repository.AccountRepository;
import com.dnd.qello.answer.domain.Answer;
import com.dnd.qello.answer.domain.AnswerStatus;
import com.dnd.qello.answer.repository.AnswerRepository;
import com.dnd.qello.filtering.domain.FilterTargetType;
import com.dnd.qello.filtering.moderation.PublicationBlockChecker;

// 이의제기 인용 직전의 "moderation 말고 다른 공개 금지 사유" 재검증(#112).
//
// 여기서 확인하는 사유는 전부 필터링 판정과 독립적이다. 계정이 차단·삭제됐거나
// 답변 자체가 삭제됐다면, 이의제기가 받아들여졌더라도 콘텐츠를 다시 공개해서는
// 안 된다.
//
// 법적 명령처럼 아직 모델링되지 않은 공개 금지 사유는 이 구현이 알지 못한다.
// 그런 사유가 데이터 모델에 생기면 이 클래스에 조건을 추가한다 — 필터링 도메인은
// 사유 코드를 문자열로만 다루므로 함께 고칠 필요가 없다.
@Component
@Transactional(readOnly = true)
public class AnswerPublicationBlockChecker implements PublicationBlockChecker {

	private static final String TARGET_NOT_FOUND = "TARGET_NOT_FOUND";
	private static final String ANSWER_DELETED = "ANSWER_DELETED";
	private static final String ACCOUNT_BLOCKED = "ACCOUNT_BLOCKED";
	private static final String ACCOUNT_DELETED = "ACCOUNT_DELETED";
	private static final String UNSUPPORTED_TARGET_TYPE = "UNSUPPORTED_TARGET_TYPE";

	private final AnswerRepository answerRepository;
	private final AccountRepository accountRepository;

	public AnswerPublicationBlockChecker(AnswerRepository answerRepository, AccountRepository accountRepository) {
		this.answerRepository = answerRepository;
		this.accountRepository = accountRepository;
	}

	@Override
	public Optional<String> findPublicationBlockReason(FilterTargetType targetType, long targetId) {
		if (targetType != FilterTargetType.ANSWER) {
			// 이 구현이 판단할 수 없는 대상이다. 모른다를 "차단 없음"으로
			// 해석하지 않고 차단으로 돌려준다(fail-closed).
			return Optional.of(UNSUPPORTED_TARGET_TYPE);
		}
		Optional<Answer> answer = answerRepository.findById(targetId);
		if (answer.isEmpty()) {
			return Optional.of(TARGET_NOT_FOUND);
		}
		if (answer.get().getStatus() == AnswerStatus.DELETED) {
			return Optional.of(ANSWER_DELETED);
		}
		Optional<Account> account = accountRepository.findById(answer.get().getAuthorId());
		if (account.isEmpty()) {
			return Optional.of(TARGET_NOT_FOUND);
		}
		AccountStatus status = account.get().getStatus();
		if (status == AccountStatus.BLOCKED) {
			return Optional.of(ACCOUNT_BLOCKED);
		}
		if (status == AccountStatus.DELETED) {
			return Optional.of(ACCOUNT_DELETED);
		}
		return Optional.empty();
	}
}
