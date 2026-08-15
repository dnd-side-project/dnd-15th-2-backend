package com.dnd.qello.filtering.repository;

import java.time.Instant;

import com.dnd.qello.filtering.domain.SnapshotHealth;

public interface SnapshotHealthRepository {

	/**
	 * modelSnapshot 행이 없으면 HEALTHY로 생성한 뒤, 있으면 그대로 잠가서 반환한다
	 * ({@code SELECT ... FOR UPDATE}). 같은 트랜잭션에서 이 메서드가 반환한 값을
	 * 수정해 {@link #save(SnapshotHealth)}로 저장할 때까지 다른 트랜잭션의 갱신을
	 * 막는다 — 여러 probe 호출이 동시에 같은 snapshot의 결과를 보고해도 갱신이
	 * 유실되지 않는다.
	 */
	SnapshotHealth findOrCreateForUpdate(String modelSnapshot, Instant now);

	SnapshotHealth save(SnapshotHealth health);
}
