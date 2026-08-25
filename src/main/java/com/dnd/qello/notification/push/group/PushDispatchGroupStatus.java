package com.dnd.qello.notification.push.group;

public enum PushDispatchGroupStatus {
	COLLECTING,
	PENDING,
	PROCESSING,
	FAILED,
	COMPLETED,
	CANCELLED,
	DEAD;

	public boolean isTerminal() {
		return this == COMPLETED || this == CANCELLED || this == DEAD;
	}

	public boolean allowsTransitionTo(PushDispatchGroupStatus next) {
		if (next == null || isTerminal()) {
			return false;
		}
		return switch (this) {
			case COLLECTING -> next == PENDING || next == CANCELLED;
			case PENDING -> next == PROCESSING || next == CANCELLED || next == DEAD;
			case PROCESSING -> next == PENDING || next == FAILED || next == COMPLETED || next == CANCELLED || next == DEAD;
			case FAILED -> next == PROCESSING || next == CANCELLED || next == DEAD;
			case COMPLETED, CANCELLED, DEAD -> false;
		};
	}
}
