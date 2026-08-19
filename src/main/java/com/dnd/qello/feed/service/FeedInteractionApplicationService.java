package com.dnd.qello.feed.service;

import java.time.Clock;
import java.time.Instant;
import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.dnd.qello.answer.service.AnswerReactionService;
import com.dnd.qello.direction.service.DirectionPostService;
import com.dnd.qello.direction.service.PostReactionService;
import com.dnd.qello.direction.service.PostRecipientService;
import com.dnd.qello.feed.error.FeedErrorCode;
import com.dnd.qello.feed.error.FeedException;
import com.dnd.qello.feed.repository.PostAnswerQueryRepository.AnswerCursor;
import com.dnd.qello.feed.repository.SentPostQueryRepository.SentPostCursor;
import com.dnd.qello.feed.view.AnswerCard;
import com.dnd.qello.feed.view.SentPostCard;
import com.dnd.qello.feed.view.SentPostDetail;
import com.dnd.qello.feed.view.SentPostFilter;

import lombok.RequiredArgsConstructor;

/**
 * `내가 보낸 질문`, 답변 목록, 답변 읽음 처리, 공감의 application 경계다.
 * 계정 자격 게이트(ACTIVE USER)는 {@link AccountEligibilityGate}를 공유해, 새
 * 읽기·상호작용 경로 7개가 {@link InboxApplicationService}와 다른 기준으로 계정을
 * 걸러내지 않게 한다.
 * <p>
 * 공감 진입점은 direction.web·answer.web 컨트롤러가 호출한다. feed가 이미
 * PostRecipientService(direction)와 PostAnswerQueryService·AnswerReactionService(answer)를
 * 참조하므로 참조 방향이 새로 생기지 않는다.
 */
@Service
@RequiredArgsConstructor
public class FeedInteractionApplicationService {

	/**
	 * 상한만 여기서 강제한다. 기본값 20은 각 컨트롤러의 {@code @RequestParam(defaultValue)}가
	 * 채운다 — InboxApiSpec.list의 category 기본값과 같은 자리에 둔다.
	 */
	private static final int MAX_LIMIT = 50;

	private final AccountEligibilityGate accountEligibilityGate;
	private final SentPostQueryService sentPostQueryService;
	private final PostAnswerQueryService postAnswerQueryService;
	private final DirectionPostService directionPostService;
	private final PostRecipientService postRecipientService;
	private final PostReactionService postReactionService;
	private final AnswerReactionService answerReactionService;
	private final Clock clock;

	@Transactional(readOnly = true)
	public List<SentPostCard> listSentPosts(
		long senderId, SentPostFilter filter, Instant cursorSubmittedAt, Long cursorPostId, int limit) {
		accountEligibilityGate.require(senderId);
		int normalizedLimit = requireValidLimit(limit);
		SentPostCursor cursor = sentPostCursor(cursorSubmittedAt, cursorPostId);
		return sentPostQueryService.list(senderId, filter, cursor, normalizedLimit, clock.instant());
	}

	@Transactional(readOnly = true)
	public SentPostDetail sentPostDetail(long senderId, long postId) {
		accountEligibilityGate.require(senderId);
		return sentPostQueryService.detail(senderId, postId)
			.orElseThrow(() -> new FeedException(FeedErrorCode.SENT_POST_NOT_FOUND));
	}

	/**
	 * 자격이 없는 뷰어는 예외가 아니라 빈 목록을 받는다(design decision 5) —
	 * PostAnswerQueryRepository.findAnswers의 기존 계약을 그대로 올린다.
	 */
	@Transactional(readOnly = true)
	public List<AnswerCard> answers(
		long viewerId, long postId, Instant cursorPublishedAt, Long cursorAnswerId, int limit) {
		accountEligibilityGate.require(viewerId);
		int normalizedLimit = requireValidLimit(limit);
		AnswerCursor cursor = answerCursor(cursorPublishedAt, cursorAnswerId);
		return postAnswerQueryService.answers(viewerId, postId, cursor, normalizedLimit, clock.instant());
	}

	/** 질문자용 읽음 처리. GREATEST 전진 규칙은 DirectionPostService가 소유한다. */
	@Transactional
	public Instant markSenderAnswersRead(long senderId, long postId) {
		accountEligibilityGate.require(senderId);
		return directionPostService.markAnswersRead(senderId, postId, clock.instant()).getAnswersReadAt();
	}

	/** 수신자용 읽음 처리. GREATEST 전진 규칙은 PostRecipientService가 소유한다. */
	@Transactional
	public Instant markRecipientAnswersRead(long recipientId, long postRecipientId) {
		accountEligibilityGate.require(recipientId);
		return postRecipientService.markAnswersRead(recipientId, postRecipientId, clock.instant()).getAnswersReadAt();
	}

	@Transactional
	public long reactToPost(long postId, long reactorId) {
		accountEligibilityGate.require(reactorId);
		return postReactionService.react(postId, reactorId, clock.instant());
	}

	@Transactional
	public long cancelPostReaction(long postId, long reactorId) {
		accountEligibilityGate.require(reactorId);
		return postReactionService.cancel(postId, reactorId);
	}

	@Transactional
	public long reactToAnswer(long answerId, long reactorId) {
		accountEligibilityGate.require(reactorId);
		return answerReactionService.react(answerId, reactorId, clock.instant());
	}

	@Transactional
	public long cancelAnswerReaction(long answerId, long reactorId) {
		accountEligibilityGate.require(reactorId);
		return answerReactionService.cancel(answerId, reactorId);
	}

	private int requireValidLimit(int limit) {
		if (limit < 1 || limit > MAX_LIMIT) {
			throw new FeedException(FeedErrorCode.LIMIT_OUT_OF_RANGE, "limit", "limit은 1 이상 " + MAX_LIMIT + " 이하여야 합니다");
		}
		return limit;
	}

	private SentPostCursor sentPostCursor(Instant cursorSubmittedAt, Long cursorPostId) {
		requireBothOrNeitherCursorParam(cursorSubmittedAt, cursorPostId);
		return cursorSubmittedAt == null ? null : new SentPostCursor(cursorSubmittedAt, cursorPostId);
	}

	private AnswerCursor answerCursor(Instant cursorPublishedAt, Long cursorAnswerId) {
		requireBothOrNeitherCursorParam(cursorPublishedAt, cursorAnswerId);
		return cursorPublishedAt == null ? null : new AnswerCursor(cursorPublishedAt, cursorAnswerId);
	}

	private void requireBothOrNeitherCursorParam(Instant cursorAt, Long cursorId) {
		if ((cursorAt == null) != (cursorId == null)) {
			throw new FeedException(FeedErrorCode.CURSOR_INCOMPLETE);
		}
	}
}
