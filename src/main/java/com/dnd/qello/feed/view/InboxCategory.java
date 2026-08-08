package com.dnd.qello.feed.view;

/**
 * `내게 온 질문` 화면의 두 카테고리.
 * 새 컬럼 없이 post_recipient.status로 유도한다 — 2026-08-07 개정에서 바뀐 것은
 * 상태 기계가 아니라 그 상태를 어느 목록에 태우느냐다.
 * 두 카테고리 모두 만료 전 항목만 담는다. 답변한 질문글도 만료되면 목록에서 빠진다.
 */
public enum InboxCategory {

	/** 아직 답변하지 않은 항목. 넘김 되돌리기가 가능한 SKIP_PENDING도 여기 남는다. */
	UNANSWERED,

	/** 답변을 마친 항목. 용량은 이미 해제됐지만 만료 전까지 목록에 남는다. */
	ANSWERED
}
