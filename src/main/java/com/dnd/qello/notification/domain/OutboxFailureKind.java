package com.dnd.qello.notification.domain;

/** 워커가 관측한 실패를 재시도 정책에 전달하기 위한 최소 분류다. */
public enum OutboxFailureKind {
	RETRYABLE,
	PERMANENT
}
