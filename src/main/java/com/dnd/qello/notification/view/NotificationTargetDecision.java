package com.dnd.qello.notification.view;

/**
 * {@code GET /notifications/{id}/target}의 진입 판정 결과다. {@code navigable},
 * {@code reason}, {@code fallback}은 전부 {@code targetState}에서 파생한다 —
 * 별도 필드로 저장하지 않아 서로 어긋나는 상태 자체가 만들어질 수 없다.
 */
public record NotificationTargetDecision(
	NotificationTargetKind targetKind,
	Long targetId,
	NotificationTargetState targetState
) {

	public NotificationTargetDecision {
		if (targetKind == null) {
			throw new IllegalArgumentException("targetKind must not be null");
		}
		if (targetKind == NotificationTargetKind.NONE) {
			if (targetId != null || targetState != null) {
				throw new IllegalArgumentException("NONE target must not carry an id or a state");
			}
		} else if (targetId == null || targetState == null) {
			throw new IllegalArgumentException("non-NONE target requires an id and a state");
		}
	}

	/** 이동 가능 여부. 대상이 없으면(NONE) 판정할 상태가 없으므로 이동 불가로 본다. */
	public boolean navigable() {
		return targetState == NotificationTargetState.AVAILABLE;
	}

	/** navigable이 아닐 때만 채워진다. NONE 대상은 탓할 state가 없으므로 null이다. */
	public NotificationTargetState reason() {
		return navigable() ? null : targetState;
	}

	/**
	 * 이동할 수 없을 때 클라이언트가 대신 보여줄 화면. EXPIRED는 수신 질문글이
	 * 수신함에서는 여전히 보이므로 INBOX로 보낸다 — 그 외 이동 불가 상태(GONE·
	 * BLOCKED·HIDDEN, 그리고 대상이 아예 없는 NONE)는 어느 화면에서도 다시 보이지
	 * 않으므로 FEED_HOME으로 보낸다.
	 */
	public Fallback fallback() {
		if (navigable()) {
			return Fallback.NONE;
		}
		return targetState == NotificationTargetState.EXPIRED ? Fallback.INBOX : Fallback.FEED_HOME;
	}

	public enum Fallback {
		NONE,
		FEED_HOME,
		INBOX
	}
}
