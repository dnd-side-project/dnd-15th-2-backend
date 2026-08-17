package com.dnd.qello.question.repository;

import java.util.List;
import java.util.Optional;

import com.dnd.qello.question.domain.QuestionProposal;

public interface QuestionProposalRepository {

	QuestionProposal save(QuestionProposal proposal);

	Optional<QuestionProposal> findById(long id);

	/**
	 * 판정 transaction이 상태를 읽고 쓰는 사이에 다른 판정이 끼어들지 못하도록 행
	 * 잠금과 함께 조회한다.
	 *
	 * <p>{@code QuestionProposal}에는 version column이 없어 낙관적 잠금이 걸리지
	 * 않는다. 잠금 없이 읽으면 두 운영자의 동시 판정이 모두 `UNDER_REVIEW`를 읽고
	 * 각자 판정 이력을 남길 수 있다.</p>
	 */
	Optional<QuestionProposal> findByIdForUpdate(long id);

	List<QuestionProposal> findAllByProposerIdOrderByCreatedAtDesc(long proposerId);
}
