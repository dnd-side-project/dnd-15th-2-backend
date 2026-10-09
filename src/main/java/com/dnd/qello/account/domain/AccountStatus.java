package com.dnd.qello.account.domain;

public enum AccountStatus {
	ACTIVE,
	// 탈퇴를 요청하고 유예 기간이 끝나기를 기다리는 상태(#337). ACTIVE가 아니므로 쓰기·매칭·알림에서 빠진다.
	WITHDRAWAL_PENDING, BLOCKED, DELETED
}
