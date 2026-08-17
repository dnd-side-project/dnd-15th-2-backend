package com.dnd.qello.direction.service;

import java.time.Instant;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.dnd.qello.direction.config.SkipConfirmationProperties;
import com.dnd.qello.direction.domain.PostRecipient;
import com.dnd.qello.direction.domain.PostRecipientStatus;
import com.dnd.qello.direction.error.DirectionErrorCode;
import com.dnd.qello.direction.error.DirectionException;
import com.dnd.qello.direction.repository.PostRecipientRepository;

import lombok.RequiredArgsConstructor;

/**
 * 수신함 항목의 열람과 넘김 요청을 소유한다.
 * 넘김 확정(SKIPPED)·만료(EXPIRED)·차단(BLOCKED) 전이는 ReceiveSlotReleaseService가
 * 소유한다 — 사용자 요청이 아니라 내부 sweep/차단 이벤트로 트리거되는 전이라
 * 소유권 검증(load)이 필요 없는 이 service와 성격이 다르다.
 */
@Service
@RequiredArgsConstructor
public class PostRecipientService {

	private final PostRecipientRepository recipientRepository;
	private final SkipConfirmationProperties skipConfirmationProperties;

	/**
	 * 소유권을 검증한 뒤 도메인의 open()을 호출한다. 이미 OPENED라 도메인이 자기 자신을
	 * 그대로 반환하면(참조 동일성으로 판단) 불필요한 UPDATE를 생략한다 — 화면을 여러 번
	 * 열어도 매번 쓰기가 나가지 않게 하기 위함이다.
	 */
	@Transactional
	public PostRecipient open(long recipientId, long postRecipientId, Instant at) {
		PostRecipient recipient = loadInboxItem(recipientId, postRecipientId, at);
		if (recipient.getStatus() == PostRecipientStatus.OPENED
			|| recipient.getStatus() == PostRecipientStatus.ANSWERED
			|| recipient.getStatus() == PostRecipientStatus.SKIP_PENDING) {
			return recipient;
		}
		PostRecipient opened = recipient.open(at);
		return recipientRepository.transitionToOpened(opened, recipient.getStatus())
			.orElseThrow(this::recipientNotFound);
	}

	/** SKIP_PENDING은 되돌리기 시간 동안 수신 용량을 계속 붙잡는다. 여기서 슬롯을 해제하지 않는다. */
	@Transactional
	public PostRecipient requestSkip(long recipientId, long postRecipientId, Instant at) {
		PostRecipient recipient = loadInboxCommandItem(recipientId, postRecipientId, at);
		if (recipient.getStatus() == PostRecipientStatus.SKIP_PENDING) {
			return recipient;
		}
		PostRecipient pending = recipient.requestSkip(at);
		return recipientRepository.transitionToSkipPending(pending, recipient.getStatus())
			.orElseThrow(this::recipientNotFound);
	}

	/** 애플리케이션 경계가 동일 transaction에서 deadline을 선판정할 수 있도록 잠긴 후보를 반환한다. */
	@Transactional
	public PostRecipient findRevertCandidate(long recipientId, long postRecipientId, Instant at) {
		return loadInboxCommandItem(recipientId, postRecipientId, at);
	}

	/**
	 * 서버 시각이 유예 마감보다 엄격히 빠를 때만 되돌린다. 정확히 마감에 도달한
	 * 항목은 #126 confirm lane의 소유이므로 상태를 바꾸지 않는다.
	 */
	@Transactional
	public PostRecipient revertSkip(long recipientId, long postRecipientId, Instant at) {
		PostRecipient recipient = loadInboxCommandItem(recipientId, postRecipientId, at);
		if (recipient.getStatus() != PostRecipientStatus.SKIP_PENDING) {
			throw transitionConflict();
		}
		Instant requestedAt = recipient.getSkipRequestedAt();
		Instant revertibleUntil = requestedAt.plusSeconds(skipConfirmationProperties.skipConfirmationGraceSeconds());
		if (!at.isBefore(revertibleUntil)) {
			throw transitionConflict();
		}
		PostRecipient reverted = recipient.revertSkip();
		return recipientRepository.transitionFromSkipPending(reverted, requestedAt)
			.orElseThrow(this::transitionConflict);
	}

	/**
	 * 이 수신자가 그 질문글의 답변 목록을 읽었음을 기록한다. `새로운 답변 n개` 배지가
	 * 이 값으로 계산된다. recipient.markAnswersRead(at)는 유효성만 검증하고 결과는
	 * 버린다 — 실제 반영은 advanceAnswersReadAt()의 DB 단일 UPDATE(GREATEST 비교)로
	 * 위임해, 순서가 뒤바뀌어 도착한 요청이 이미 기록된 더 늦은 시각을 덮어쓰지 않게
	 * 한다(direction.service.DirectionPostService.markAnswersRead와 같은 이유).
	 */
	@Transactional
	public PostRecipient markAnswersRead(long recipientId, long postRecipientId, Instant at) {
		PostRecipient recipient = load(recipientId, postRecipientId);
		recipient.markAnswersRead(at);
		return recipientRepository.advanceAnswersReadAt(postRecipientId, at);
	}

	/** 존재하지 않는 경우와 남의 항목인 경우를 구분하지 않는다. 구분하면 존재 여부가 새어나간다. */
	private PostRecipient load(long recipientId, long postRecipientId) {
		return recipientRepository.findByIdAndRecipientId(postRecipientId, recipientId)
			.orElseThrow(() -> new DirectionException(
				DirectionErrorCode.RECIPIENT_NOT_FOUND, "postRecipientId", "수신 항목을 찾을 수 없습니다"));
	}

	private PostRecipient loadInboxItem(long recipientId, long postRecipientId, Instant at) {
		return recipientRepository.findInboxItemForUpdate(postRecipientId, recipientId, at)
			.orElseThrow(this::recipientNotFound);
	}

	private PostRecipient loadInboxCommandItem(long recipientId, long postRecipientId, Instant at) {
		return recipientRepository.findInboxCommandItemForUpdate(postRecipientId, recipientId, at)
			.orElseThrow(this::recipientNotFound);
	}

	private DirectionException recipientNotFound() {
		return new DirectionException(
			DirectionErrorCode.RECIPIENT_NOT_FOUND, "postRecipientId", "수신 항목을 찾을 수 없습니다");
	}

	private DirectionException transitionConflict() {
		return new DirectionException(
			DirectionErrorCode.INVALID_RECIPIENT_STATE, "status", "수신 상태를 변경할 수 없습니다");
	}
}
