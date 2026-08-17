package com.dnd.qello.answer.service;

import java.time.Clock;
import java.time.Instant;
import java.util.List;

import org.springframework.stereotype.Service;

import com.dnd.qello.account.domain.Account;
import com.dnd.qello.account.domain.AccountRole;
import com.dnd.qello.account.domain.AccountStatus;
import com.dnd.qello.account.repository.AccountRepository;
import com.dnd.qello.answer.domain.Answer;
import com.dnd.qello.answer.error.AnswerErrorCode;
import com.dnd.qello.answer.error.AnswerException;

/**
 * 인증 사용자와 HTTP 입력을 {@link AnswerSubmissionService} command로 변환하는 application
 * facade(GitHub #125). web 계층에는 작성자, 시각, 지역·방위·거리를 두지 않는다 — 이
 * facade가 JWT subject에 해당하는 ACTIVE USER와 서버 Clock을 해석한 뒤 하위 transaction
 * service에 전달한다.
 */
@Service
public class AnswerSubmissionApplicationService {

	private static final int IDEMPOTENCY_KEY_MAX_LENGTH = 200;

	private final AccountRepository accountRepository;
	private final AnswerSubmissionService submissionService;
	private final Clock clock;

	public AnswerSubmissionApplicationService(
		AccountRepository accountRepository, AnswerSubmissionService submissionService, Clock clock
	) {
		this.accountRepository = accountRepository;
		this.submissionService = submissionService;
		this.clock = clock;
	}

	public Answer submit(long authorId, String idempotencyKey, long postRecipientId, String bodyText, List<Long> mediaIds) {
		validateIdempotencyKey(idempotencyKey);
		ensureActiveUser(authorId);
		Instant submittedAt = clock.instant();
		return submissionService.submit(authorId, idempotencyKey,
			new AnswerSubmissionService.SubmitCommand(postRecipientId, bodyText, mediaIds, submittedAt));
	}

	private void ensureActiveUser(long authorId) {
		if (authorId <= 0) {
			throw new AnswerException(AnswerErrorCode.INVALID_ID, "authorId", "인증 사용자 식별자가 유효하지 않습니다");
		}
		Account account = accountRepository.findById(authorId)
			.orElseThrow(() -> new AnswerException(
				AnswerErrorCode.ACCOUNT_NOT_FOUND, "authorId", "답변을 제출할 계정을 찾을 수 없습니다"));
		if (account.getRole() != AccountRole.USER || account.getStatus() != AccountStatus.ACTIVE) {
			throw new AnswerException(
				AnswerErrorCode.ACCOUNT_NOT_ELIGIBLE, "authorId", "현재 계정은 답변을 제출할 수 없습니다");
		}
	}

	private static void validateIdempotencyKey(String idempotencyKey) {
		if (idempotencyKey == null || idempotencyKey.isBlank() || idempotencyKey.length() > IDEMPOTENCY_KEY_MAX_LENGTH) {
			throw new AnswerException(AnswerErrorCode.INVALID_TEXT, "idempotencyKey", "Idempotency-Key는 1~200자여야 합니다");
		}
	}
}
