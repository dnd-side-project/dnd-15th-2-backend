package com.dnd.qello.answer.service;

import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import com.dnd.qello.answer.repository.AnswerRepository;
import com.dnd.qello.filtering.domain.FilterTargetType;
import com.dnd.qello.filtering.moderation.AppealTargetOwnershipChecker;

// 필터링 시스템의 소유권 질의를 답변 도메인이 답한다(#112).
//
// 인터페이스는 filtering이 소유하고 이 구현체만 answer를 참조하므로,
// filtering -> answer 방향 의존은 생기지 않는다.
@Component
@Transactional(readOnly = true)
public class AnswerAppealOwnershipChecker implements AppealTargetOwnershipChecker {

	private final AnswerRepository answerRepository;

	public AnswerAppealOwnershipChecker(AnswerRepository answerRepository) {
		this.answerRepository = answerRepository;
	}

	@Override
	public boolean isOwnedBy(FilterTargetType targetType, long targetId, long userId) {
		if (targetType != FilterTargetType.ANSWER) {
			return false;
		}
		// 소유권을 쿼리 조건에 담아, 남의 답변이면 존재 여부와 무관하게 빈 결과가 된다.
		return answerRepository.findByIdAndAuthorId(targetId, userId).isPresent();
	}
}
