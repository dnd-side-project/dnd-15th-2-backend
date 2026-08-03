package com.dnd.qello.notification.domain;

public enum OutboxStatus {
	PENDING,
	PROCESSING,
	PROCESSED,
	FAILED,
	DEAD
}
