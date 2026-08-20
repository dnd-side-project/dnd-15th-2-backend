package com.dnd.qello.notification.view;

/**
 * {@code GET /notifications/{id}/target}의 진입 판정 결과다. {@code navigable}과
 * {@code reason}은 {@code targetState}에서 파생한다 — 별도 필드로 저장하지 않아
 * 둘이 서로 어긋나는 상태 자체가 만들어질 수 없다.
 */
public record NotificationTargetDecision(
	NotificationTargetKind targetKind,
	Long targetId,
	NotificationTargetState targetState,
	Fallback fallback
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
		if (fallback == null) {
			throw new IllegalArgumentException("fallback must not be null");
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

	/** 이동할 수 없을 때 클라이언트가 대신 보여줄 화면. */
	public enum Fallback {
		NONE,
		FEED_HOME,
		INBOX
	}
}
