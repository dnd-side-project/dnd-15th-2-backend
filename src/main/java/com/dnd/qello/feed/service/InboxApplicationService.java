package com.dnd.qello.feed.service;

import java.time.Clock;
import java.time.Instant;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.dnd.qello.account.domain.Account;
import com.dnd.qello.account.domain.AccountRole;
import com.dnd.qello.account.domain.AccountStatus;
import com.dnd.qello.account.repository.AccountRepository;
import com.dnd.qello.direction.config.SkipConfirmationProperties;
import com.dnd.qello.direction.domain.PostRecipient;
import com.dnd.qello.direction.error.DirectionErrorCode;
import com.dnd.qello.direction.error.DirectionException;
import com.dnd.qello.direction.service.PostRecipientService;
import com.dnd.qello.feed.error.FeedErrorCode;
import com.dnd.qello.feed.error.FeedException;
import com.dnd.qello.feed.view.InboxCategory;
import com.dnd.qello.feed.view.InboxDetail;
import com.dnd.qello.feed.view.InboxListing;

import lombok.RequiredArgsConstructor;

/** 인증 subject를 검증하고 수신함 조회와 사용자 상태 명령의 feature 경계를 제공한다. */
@Service
@RequiredArgsConstructor
public class InboxApplicationService {

	private final AccountRepository accountRepository;
	private final InboxQueryService queryService;
	private final PostRecipientService postRecipientService;
	private final Clock clock;
	private final SkipConfirmationProperties skipConfirmationProperties;

	@Transactional(readOnly = true)
	public InboxListing list(long recipientId, InboxCategory category, String directionSegmentKey) {
		requireEligibleAccount(recipientId);
		Instant at = clock.instant();
		return queryService.list(recipientId, category, directionSegmentKey, at);
	}

	/** 최초 OPENED 전이와 전이 후 상세 projection 조회를 같은 transaction과 시각으로 묶는다. */
	@Transactional
	public InboxDetail detail(long recipientId, long postRecipientId) {
		requireEligibleAccount(recipientId);
		Instant at = clock.instant();
		try {
			postRecipientService.open(recipientId, postRecipientId, at);
		} catch (DirectionException exception) {
			throw mapCommandException(exception);
		}
		return queryService.detail(recipientId, postRecipientId, at)
			.orElseThrow(() -> new FeedException(FeedErrorCode.INBOX_ITEM_NOT_FOUND));
	}

	@Transactional
	public PostRecipient skip(long recipientId, long postRecipientId) {
		requireEligibleAccount(recipientId);
		Instant at = clock.instant();
		try {
			return postRecipientService.requestSkip(recipientId, postRecipientId, at);
		} catch (DirectionException exception) {
			throw mapCommandException(exception);
		}
	}

	@Transactional
	public PostRecipient revertSkip(long recipientId, long postRecipientId) {
		requireEligibleAccount(recipientId);
		Instant at = clock.instant();
		try {
			PostRecipient candidate = postRecipientService.findRevertCandidate(recipientId, postRecipientId, at);
			if (!at.isBefore(revertibleUntil(candidate))) {
				throw new FeedException(FeedErrorCode.INBOX_TRANSITION_CONFLICT);
			}
			return postRecipientService.revertSkip(recipientId, postRecipientId, at);
		} catch (DirectionException exception) {
			throw mapCommandException(exception);
		}
	}

	/** 응답 DTO가 서버 설정과 같은 되돌리기 마감을 사용하도록 한 계산 경계다. */
	public Instant revertibleUntil(PostRecipient recipient) {
		if (recipient.getSkipRequestedAt() == null) {
			throw new FeedException(FeedErrorCode.INBOX_TRANSITION_CONFLICT);
		}
		return recipient.getSkipRequestedAt()
			.plusSeconds(skipConfirmationProperties.skipConfirmationGraceSeconds());
	}

	private void requireEligibleAccount(long recipientId) {
		Account account = accountRepository.findById(recipientId)
			.orElseThrow(() -> new FeedException(FeedErrorCode.INBOX_ACCOUNT_NOT_FOUND));
		if (account.getRole() != AccountRole.USER || account.getStatus() != AccountStatus.ACTIVE) {
			throw new FeedException(FeedErrorCode.INBOX_ACCOUNT_NOT_ELIGIBLE);
		}
	}

	private FeedException mapCommandException(DirectionException exception) {
		if (exception.getErrorCode() == DirectionErrorCode.INVALID_RECIPIENT_STATE) {
			return new FeedException(FeedErrorCode.INBOX_TRANSITION_CONFLICT, "status", exception.getReason(), exception);
		}
		return new FeedException(
			FeedErrorCode.INBOX_ITEM_NOT_FOUND, "postRecipientId", "수신함 항목을 찾을 수 없습니다.", exception);
	}
}
