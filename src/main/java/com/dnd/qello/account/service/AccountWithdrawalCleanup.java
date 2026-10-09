package com.dnd.qello.account.service;

import java.time.Instant;

/**
 * 탈퇴 요청과 완료 때 다른 모듈이 그 사용자의 데이터를 정리하는 지점이다(#337).
 *
 * <p>
 * 자격증명(auth), 푸시 기기(notification), 위치(direction)는 각 모듈이 이 인터페이스를 구현해 정리한다. 그
 * 모듈들은 이미 account를 참조하므로 account가 직접 부르면 순환 의존이 생긴다. 두 메서드 모두 계정 상태를 바꾸는 트랜잭션
 * 안에서 호출되며, 예외를 던지면 계정 상태 변경까지 함께 rollback된다.
 */
public interface AccountWithdrawalCleanup {

	/** 계정이 WITHDRAWAL_PENDING이 된 직후 호출된다. 철회할 수 있으므로 되돌릴 수 없는 정리는 하지 않는다. */
	default void onWithdrawalRequested(long userId, Instant requestedAt) {
	}

	/** 유예가 끝나 계정이 DELETED가 된 직후 호출된다. */
	default void onWithdrawalCompleted(long userId, Instant completedAt) {
	}

}
