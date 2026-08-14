package com.dnd.qello.filtering.repository;

import java.time.Instant;

import com.dnd.qello.filtering.domain.FilterReleaseRetryGate;

public interface FilterReleaseRetryGateRepository {

	/**
	 * release 게이트 행이 없으면 HEALTHY로 생성한 뒤, 있으면 그대로 잠가서 반환한다
	 * (`SELECT ... FOR UPDATE`). 같은 트랜잭션에서 이 메서드가 반환한 값을 수정해
	 * {@link #save(FilterReleaseRetryGate)}로 저장할 때까지 다른 트랜잭션의 갱신을
	 * 막는다 — 여러 worker가 동시에 같은 release의 실패/성공을 보고해도 갱신이
	 * 유실되지 않는다(INV-RTY-007).
	 */
	FilterReleaseRetryGate findOrCreateForUpdate(long filterReleaseId, Instant now);

	FilterReleaseRetryGate save(FilterReleaseRetryGate gate);
}
