package com.dnd.qello.direction.service;

import java.time.Instant;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.dnd.qello.direction.domain.PostRecipient;
import com.dnd.qello.direction.error.DirectionErrorCode;
import com.dnd.qello.direction.error.DirectionException;
import com.dnd.qello.direction.repository.PostRecipientRepository;

/**
 * 수신함 항목의 열람과 넘김 요청을 소유한다.
 * 넘김 확정(SKIPPED)과 만료(EXPIRED)는 후속 워커의 몫이며 이 service의 범위가 아니다.
 */
@Service
public class PostRecipientService {

	private final PostRecipientRepository recipientRepository;

	public PostRecipientService(PostRecipientRepository recipientRepository) {
		this.recipientRepository = recipientRepository;
	}

	/**
	 * 소유권을 검증한 뒤 도메인의 open()을 호출한다. 이미 OPENED라 도메인이 자기 자신을
	 * 그대로 반환하면(참조 동일성으로 판단) 불필요한 UPDATE를 생략한다 — 화면을 여러 번
	 * 열어도 매번 쓰기가 나가지 않게 하기 위함이다.
	 */
	@Transactional
	public PostRecipient open(long recipientId, long postRecipientId, Instant at) {
		PostRecipient recipient = load(recipientId, postRecipientId);
		PostRecipient opened = recipient.open(at);
		return opened == recipient ? recipient : recipientRepository.save(opened);
	}

	/** SKIP_PENDING은 되돌리기 시간 동안 수신 용량을 계속 붙잡는다. 여기서 슬롯을 해제하지 않는다. */
	@Transactional
	public PostRecipient requestSkip(long recipientId, long postRecipientId, Instant at) {
		return recipientRepository.save(load(recipientId, postRecipientId).requestSkip(at));
	}

	/**
	 * 넘김 요청(SKIP_PENDING)을 취소하고 열람 이력에 따라 이전 상태로 되돌린다.
	 * 확정된(SKIPPED) 넘김은 되돌릴 수 없다 — 도메인의 revertSkip()이 그 경우
	 * INVALID_RECIPIENT_STATE를 던진다.
	 */
	@Transactional
	public PostRecipient revertSkip(long recipientId, long postRecipientId) {
		return recipientRepository.save(load(recipientId, postRecipientId).revertSkip());
	}

	/** 존재하지 않는 경우와 남의 항목인 경우를 구분하지 않는다. 구분하면 존재 여부가 새어나간다. */
	private PostRecipient load(long recipientId, long postRecipientId) {
		return recipientRepository.findByIdAndRecipientId(postRecipientId, recipientId)
			.orElseThrow(() -> new DirectionException(
				DirectionErrorCode.RECIPIENT_NOT_FOUND, "postRecipientId", "수신 항목을 찾을 수 없습니다"));
	}
}
