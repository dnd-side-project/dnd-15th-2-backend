package com.dnd.qello.direction.repository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import com.dnd.qello.direction.domain.PostRecipient;
import com.dnd.qello.direction.domain.PostRecipientStatus;

public interface PostRecipientRepository {
	PostRecipient save(PostRecipient recipient);
	List<PostRecipient> findAllByPostId(long postId);

	/**
	 * status가 여전히 previousStatus일 때만 answered로 전이하고 저장한다. 영향받은 행이
	 * 없으면(이미 다른 트랜잭션이 먼저 ANSWERED로 전이시킨 경우) empty를 반환한다 — 호출자는
	 * 이 empty를 신호로 슬롯 회수(receive state release)를 건너뛰어, 동시에 들어온 publish()
	 * 재시도가 슬롯을 중복 회수하지 않도록 한다.
	 */
	Optional<PostRecipient> transitionToAnswered(PostRecipient answered, PostRecipientStatus previousStatus);

	/**
	 * 소유권 검증이 없는 조회다. 이미 다른 경로로 자격을 확인한 내부 서버 로직에서만
	 * 쓴다 — 예: AnswerReactionService가 answer_id → post_recipient_id로 내려갈 때.
	 * 사용자 입력으로 직접 이 메서드를 호출하지 않는다. 그런 경로는
	 * findByIdAndRecipientId를 쓴다.
	 */
	Optional<PostRecipient> findById(long id);

	/** fan-out transaction의 현재 자격 판정을 직렬화하기 위해 수신 행을 잠근다. */
	Optional<PostRecipient> findByIdForUpdate(long id);

	/** 소유권을 쿼리 조건에 포함한다. 남의 항목이면 빈 결과이며 예외를 던지지 않는다. */
	Optional<PostRecipient> findByIdAndRecipientId(long id, long recipientId);

	/**
	 * 상세 열람 자격(ACTIVE·미삭제 질문글, 상태·만료, 양방향 활성 차단)을 포함해
	 * 수신 행을 잠근다. ANSWERED는 만료 후에도, SKIP_PENDING은 유예 중 상세 자격을
	 * 유지한다.
	 */
	Optional<PostRecipient> findInboxItemForUpdate(long id, long recipientId, Instant at);

	/**
	 * 사용자 상태 명령이 가능한 미종결 수신 행만 동일한 visibility 조건으로 잠근다.
	 * 슬롯을 해제한 terminal 행은 반환하지 않는다.
	 */
	Optional<PostRecipient> findInboxCommandItemForUpdate(long id, long recipientId, Instant at);

	Optional<PostRecipient> findByPostIdAndRecipientId(long postId, long recipientId);

	/**
	 * answers_read_at을 GREATEST(현재값, at)로 갱신하는 단일 UPDATE다.
	 * DirectionPostRepository.advanceAnswersReadAt과 같은 이유로 read-then-write가
	 * 아닌 이 방식을 쓴다 — 순서가 뒤바뀌어 도착한 요청이 이미 기록된 더 늦은 시각을
	 * 덮어쓰지 않게 한다.
	 */
	PostRecipient advanceAnswersReadAt(long id, Instant at);

	/** 만료 sweep 후보(AVAILABLE/DISCOVERED/OPENED이고 소속 질문글이 at 이전에 만료됨). */
	List<PostRecipient> findExpirableAsOf(Instant at);

	/** 되돌리기 유예가 지난 SKIP_PENDING 후보. deadline은 호출자가 유예 시간을 뺀 값을 넘긴다. */
	List<PostRecipient> findConfirmableSkips(Instant deadline);

	/** blockerId 자신의 수신 항목 중 blockedSenderId가 보낸 질문글에 대한 미종결 항목. */
	List<PostRecipient> findBlockableFor(long blockerId, long blockedSenderId);

	/** status가 여전히 previousStatus일 때만 EXPIRED로 전이한다. transitionToAnswered와 같은 낙관적 패턴이다. */
	Optional<PostRecipient> transitionToExpired(PostRecipient expired, PostRecipientStatus previousStatus);

	/** status가 여전히 previousStatus(SKIP_PENDING)일 때만 SKIPPED로 확정한다. */
	Optional<PostRecipient> transitionToSkipped(PostRecipient skipped, PostRecipientStatus previousStatus);

	/** status가 여전히 previousStatus일 때만 BLOCKED로 전이한다. */
	Optional<PostRecipient> transitionToBlocked(PostRecipient blocked, PostRecipientStatus previousStatus);

	/** 최초 상세 열람을 previousStatus 조건으로 OPENED로 전이한다. */
	Optional<PostRecipient> transitionToOpened(PostRecipient opened, PostRecipientStatus previousStatus);

	/** 최초 넘김 요청만 SKIP_PENDING으로 전이한다. 반복 요청은 호출자가 기존 행을 반환한다. */
	Optional<PostRecipient> transitionToSkipPending(PostRecipient pending, PostRecipientStatus previousStatus);

	/** 같은 최초 요청 시각을 가진 SKIP_PENDING일 때만 이전 상태로 복원한다. */
	Optional<PostRecipient> transitionFromSkipPending(PostRecipient reverted, Instant expectedSkipRequestedAt);
}
