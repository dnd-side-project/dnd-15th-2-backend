package com.dnd.qello.direction.service;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.dnd.qello.answer.repository.AnswerRepository;
import com.dnd.qello.direction.config.SkipConfirmationProperties;
import com.dnd.qello.direction.domain.PostRecipient;
import com.dnd.qello.direction.domain.PostRecipientStatus;
import com.dnd.qello.direction.error.DirectionErrorCode;
import com.dnd.qello.direction.error.DirectionException;
import com.dnd.qello.direction.repository.PostRecipientRepository;
import com.dnd.qello.direction.repository.RecipientReceiveStateRepository;

import lombok.RequiredArgsConstructor;

/**
 * 만료·넘김확정·차단 세 경로의 수신 슬롯 해제를 소유한다. 셋 다 사용자 요청이
 * 아니라 내부 sweep 또는 다른 기능(SafetyService.block)의 이벤트로 트리거되므로
 * postRecipientId만으로 동작하고 소유권 검증을 하지 않는다 — 이미 다른 경로로
 * 대상이 확정된 뒤 호출된다는 전제다({@link PostRecipientRepository#findById}와
 * 같은 전제).
 *
 * 각 전이 메서드는 한 행을 대상으로 하며 그 자체로 하나의 트랜잭션이다. 여러 행을
 * 훑는 sweep(만료·넘김확정)은 이 메서드를 행마다 반복 호출하는 형태로 구성한다 —
 * 한 행의 실패가 이미 처리된 다른 행의 커밋을 되돌리지 않게 하기 위함이다. sweep을
 * 실제로 구동하는 진입점(스케줄러 등)은 이 이슈의 범위 밖이다.
 *
 * 예외: {@link #blockAllPendingFor}는 SafetyService.block()과 같은 트랜잭션에서
 * 차단자의 미종결 항목 전부를 원자적으로 전이해야 하므로, 같은 빈 안에서
 * {@link #block(PostRecipient, Instant)}를 자기 호출해 프록시의 트랜잭션 전파를
 * 우회한다 — 여러 행이 하나의 트랜잭션으로 묶인다.
 */
@Service
@RequiredArgsConstructor
public class ReceiveSlotReleaseService {

	private final PostRecipientRepository recipientRepository;
	private final RecipientReceiveStateRepository receiveStateRepository;
	private final SkipConfirmationProperties skipConfirmationProperties;
	private final AnswerRepository answerRepository;

	/** 만료 sweep 후보. AVAILABLE/DISCOVERED/OPENED이고 소속 질문글이 at 이전에 만료된 항목이다. */
	public List<PostRecipient> findExpirable(Instant at) {
		return recipientRepository.findExpirableAsOf(at);
	}

	/** 넘김확정 sweep 후보. 되돌리기 유예(설정값)가 지난 SKIP_PENDING 항목이다. */
	public List<PostRecipient> findConfirmableSkips(Instant at) {
		return recipientRepository.findConfirmableSkips(confirmationDeadline(at));
	}

	/**
	 * 처리량이 제한된 만료 sweep 후보. RecipientExpirationSweepWorker의 batch 크기를
	 * 그대로 전달한다. 정렬은 JdbcPostRecipientRepository의 SQL이 소유한다
	 * (dp.expires_at, pr.id) — 기아를 막는 결정적 순서다.
	 */
	public List<PostRecipient> findExpirable(Instant at, int limit) {
		return recipientRepository.findExpirableAsOf(at, limit);
	}

	/**
	 * 처리량이 제한된 넘김확정 sweep 후보. 유예 계산은 findConfirmableSkips(Instant)와
	 * 동일하게 이 메서드가 소유한다 — SkipConfirmationSweepWorker는 batch 시각과 limit만
	 * 넘긴다.
	 */
	public List<PostRecipient> findConfirmableSkips(Instant at, int limit) {
		return recipientRepository.findConfirmableSkips(confirmationDeadline(at), limit);
	}

	/** blockerId 자신의 수신 항목 중 blockedSenderId가 보낸 질문글에 대한 미종결 항목. */
	public List<PostRecipient> findBlockable(long blockerId, long blockedSenderId) {
		return recipientRepository.findBlockableFor(blockerId, blockedSenderId);
	}

	/**
	 * 만료 전이. 재조회 시점에 이미 다른 전이(답변 등)로 소스 상태를 벗어났으면
	 * 예외 대신 빈 Optional을 반환한다 — 동시 실행 경쟁의 정상적인 결과다
	 * (AnswerNotificationService.publish()가 이미 PUBLISHED인 답변을 조기 반환하는
	 * 것과 같은 판단이다).
	 *
	 * findExpirable의 후보 조회는 잠금 없이 SUBMITTED/SAFETY_CHECKING 답변 유무를
	 * 스냅샷으로 확인한다. 그 조회와 이 메서드 사이에 답변 제출이 커밋되면 후보 목록은
	 * 낡은 상태가 된다. 답변 제출(findInboxCommandItemForUpdate)이 같은 행에 FOR UPDATE
	 * 잠금을 거는 것과 동일한 잠금(findByIdForUpdate)을 여기서도 획득해 두 트랜잭션을
	 * 직렬화하고, 잠금 안에서 검사 중 답변 유무를 다시 확인해야 만료 전 제출된 답변의
	 * 자격을 보존할 수 있다.
	 */
	@Transactional
	public Optional<PostRecipient> expire(long postRecipientId, Instant at) {
		PostRecipient candidate =
			recipientRepository.findByIdForUpdate(postRecipientId).orElseThrow(this::recipientNotFound);
		if (!candidate.isOpenForTransition()) {
			return Optional.empty();
		}
		if (answerRepository.hasPendingAnswerForPostRecipient(postRecipientId)) {
			return Optional.empty();
		}
		PostRecipient expired = candidate.expire(at);
		Optional<PostRecipient> saved = recipientRepository.transitionToExpired(expired, candidate.getStatus());
		saved.ifPresent(ignored -> receiveStateRepository.release(candidate.getRecipientId(), at));
		return saved;
	}

	/**
	 * 넘김 확정. 유예 시간을 이 메서드가 직접 재확인한다 — findConfirmableSkips는
	 * sweep의 후보 선별(효율)을 위한 것이고, 이 메서드가 호출자와 무관하게 그 자체로
	 * 옳아야 한다. PostRecipient.confirmSkip() 도메인 메서드는 유예 시간을 모르므로
	 * (direction.domain.PostRecipient 참고) 그 판단은 여기서 한다.
	 */
	@Transactional
	public Optional<PostRecipient> confirmSkip(long postRecipientId, Instant at) {
		PostRecipient candidate = recipientRepository.findById(postRecipientId).orElseThrow(this::recipientNotFound);
		if (candidate.getStatus() != PostRecipientStatus.SKIP_PENDING) {
			return Optional.empty();
		}
		if (candidate.getSkipRequestedAt().isAfter(confirmationDeadline(at))) {
			return Optional.empty();
		}
		PostRecipient confirmed = candidate.confirmSkip(at);
		Optional<PostRecipient> saved = recipientRepository.transitionToSkipped(confirmed, candidate.getStatus());
		saved.ifPresent(ignored -> receiveStateRepository.release(candidate.getRecipientId(), at));
		return saved;
	}

	/** 차단 전이. 이미 ANSWERED이거나 다른 terminal 상태인 행은 손대지 않는다. */
	@Transactional
	public Optional<PostRecipient> block(long postRecipientId, Instant at) {
		return block(recipientRepository.findById(postRecipientId).orElseThrow(this::recipientNotFound), at);
	}

	/**
	 * 이미 조회한 PostRecipient로 차단 전이한다. blockAllPendingFor처럼 findBlockable로
	 * 후보를 이미 읽어온 호출자가 같은 행을 postRecipientId로 다시 조회하지 않게 한다.
	 */
	@Transactional
	public Optional<PostRecipient> block(PostRecipient candidate, Instant at) {
		if (!candidate.isBlockable()) {
			return Optional.empty();
		}
		PostRecipient blocked = candidate.block(at);
		Optional<PostRecipient> saved = recipientRepository.transitionToBlocked(blocked, candidate.getStatus());
		saved.ifPresent(ignored -> receiveStateRepository.release(candidate.getRecipientId(), at));
		return saved;
	}

	/**
	 * blockerId 자신의 수신 항목 중 blockedSenderId가 보낸 질문글에 대한 미종결 항목을
	 * 전부 차단 전이한다. SafetyService.block()의 진입점 — 호출자가 findBlockable과
	 * 반복 호출을 직접 조립하지 않도록 후보 조회와 반복을 이 메서드가 소유한다.
	 */
	@Transactional
	public int blockAllPendingFor(long blockerId, long blockedSenderId, Instant at) {
		int releasedCount = 0;
		for (PostRecipient candidate : findBlockable(blockerId, blockedSenderId)) {
			if (block(candidate, at).isPresent()) {
				releasedCount++;
			}
		}
		return releasedCount;
	}

	private DirectionException recipientNotFound() {
		return new DirectionException(
			DirectionErrorCode.RECIPIENT_NOT_FOUND, "postRecipientId", "수신 항목을 찾을 수 없습니다");
	}

	private Instant confirmationDeadline(Instant at) {
		return at.minusSeconds(skipConfirmationProperties.skipConfirmationGraceSeconds());
	}
}
