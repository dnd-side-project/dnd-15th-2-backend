package com.dnd.qello.direction.repository;

import java.time.Instant;
import java.util.Optional;

import com.dnd.qello.direction.domain.DirectionPost;

public interface DirectionPostRepository {
	DirectionPost save(DirectionPost post);
	Optional<DirectionPost> findById(long id);
	Optional<DirectionPost> findBySenderAndIdempotencyKey(long senderId, String idempotencyKey);

	/** 소유권을 쿼리 조건에 포함한다. 남의 질문글이면 빈 결과다. */
	Optional<DirectionPost> findByIdAndSenderId(long id, long senderId);

	/**
	 * answers_read_at을 max(기존 값, at)으로만 전진시킨다. save()의 전체 행 덮어쓰기 대신
	 * 이 연산을 쓰면, 순서가 뒤바뀌어 도착한 두 읽음 요청 중 더 이른 시각이 더 늦은 시각을
	 * 덮어쓰는 lost update를 DB 단일 UPDATE 안에서 막는다.
	 */
	DirectionPost advanceAnswersReadAt(long id, Instant at);
}
