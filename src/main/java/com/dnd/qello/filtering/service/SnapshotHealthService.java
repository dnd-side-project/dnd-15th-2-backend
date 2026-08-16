package com.dnd.qello.filtering.service;

import java.time.Clock;
import java.time.Instant;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.dnd.qello.filtering.domain.SnapshotHealth;
import com.dnd.qello.filtering.repository.SnapshotHealthRepository;

// snapshot health 조회와 PERMANENT_CONFIRMED 운영자 승인의 유일한 쓰기 진입점(#109).
// FilterReleaseRegistryService(#104)와 동일하게 alias나 다른 자동 경로는 없다.
//
// SnapshotHealthProbeRecorder(probe 반영)와 달리 이 서비스는 실제 운영 수치
// (SnapshotHealthPolicy) 없이 동작하므로 Spring bean으로 등록한다 — 운영자 승인
// endpoint는 값 확정을 기다릴 필요가 없다.
@Service
@Transactional(readOnly = true)
public class SnapshotHealthService {

	private final SnapshotHealthRepository snapshotHealthRepository;
	private final Clock clock;

	public SnapshotHealthService(SnapshotHealthRepository snapshotHealthRepository, Clock clock) {
		this.snapshotHealthRepository = snapshotHealthRepository;
		this.clock = clock;
	}

	@Transactional
	public SnapshotHealth find(String modelSnapshot) {
		return snapshotHealthRepository.findOrCreateForUpdate(modelSnapshot, Instant.now(clock));
	}

	// PERMANENT_SUSPECTED에서만 허용되는 운영자 승인(INV-HLT-005). confirmedAt/
	// confirmedByOperatorUserId가 이 승인의 감사 기록을 겸한다 — un-confirm 경로가
	// 이슈 범위 밖이라 별도 이력 테이블 없이도 "누가 언제 승인했는지"가 유일하게
	// 보존된다.
	@Transactional
	public SnapshotHealth confirmPermanent(String modelSnapshot, long operatorUserId) {
		Instant now = Instant.now(clock);
		SnapshotHealth current = snapshotHealthRepository.findOrCreateForUpdate(modelSnapshot, now);
		SnapshotHealth confirmed = current.confirmPermanent(operatorUserId, now);
		return snapshotHealthRepository.save(confirmed);
	}
}
