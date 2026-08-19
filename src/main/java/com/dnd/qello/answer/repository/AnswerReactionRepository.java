package com.dnd.qello.answer.repository;

import java.util.Optional;

import com.dnd.qello.answer.domain.AnswerReaction;

public interface AnswerReactionRepository {

	/**
	 * 열람 자격(질문자 또는 그 질문글의 수신 자격자) 검증은
	 * {@code ct_answer_reaction_reactor_can_view}, 즉 {@code DEFERRABLE INITIALLY DEFERRED}
	 * constraint trigger가 맡는다. 그래서 자격 위반은 이 메서드 호출 시점이 아니라 감싸는
	 * transaction이 commit되는 시점에 드러난다 — 호출자에게 ambient transaction이 있는지에
	 * 따라 이 호출로부터 여러 statement, 여러 stack frame 뒤일 수 있다. flush 시점에 즉시
	 * 실패가 드러나는 {@link com.dnd.qello.direction.repository.PostReactionRepository#react}와
	 * 대비된다. 두 reaction 타입 모두 JPA로 구현하고 자격 검증은 지연 trigger로 강제하는 이
	 * 설계는 design decision D6을 따른다.
	 * <p>
	 * PK는 (answer_id, reactor_id) 복합키다 — 볼 수 있는 사람 전원이 같은 답변에 각자
	 * 공감할 수 있으므로, 한 답변에 붙는 공감 행은 여럿일 수 있고 그중 한 사용자당 한 건만
	 * 허용된다.
	 * <p>
	 * 같은 transaction 안에서 {@link #cancel}을 호출한 직후 동일 key로 이 메서드를 다시 호출하는
	 * 것은 안전하지 않다 — Hibernate의 flush 순서상 아직 실행되지 않은 delete와 동일 PK의 insert가
	 * 충돌할 수 있다. 취소 후 재반응이 필요한 호출자는 이 repo의 통합 테스트가 하는 것처럼 두 개의
	 * 별도 transaction으로 나눠 호출해야 한다.
	 * <p>
	 * {@code createdAt}은 DB의 {@code DEFAULT clock_timestamp()}가 아니라 애플리케이션이 주입한
	 * {@link java.time.Clock}으로 채운 값을 그대로 저장한다(이 repo의 {@code JpaAuditingConfiguration}이
	 * 채택한 것과 같은 원칙이므로, 이후 호출자가 DB 기본값이 실제로 동작한다고 오해하지 않도록 한다).
	 */
	AnswerReaction react(AnswerReaction reaction);

	void cancel(long answerId, long reactorId);

	Optional<AnswerReaction> findByAnswerIdAndReactorId(long answerId, long reactorId);

	/**
	 * 그 답변이 받은 공감 총수. 응답에 서버가 센 값을 실어 클라이언트가 직접 증감하며
	 * 생기는 어긋남을 없앤다.
	 */
	long countByAnswerId(long answerId);
}
