package com.dnd.qello.filtering.moderation;

import java.time.Clock;
import java.time.Instant;

import org.springframework.transaction.support.TransactionTemplate;

import com.dnd.qello.filtering.domain.ModerationFailureClassification;
import com.dnd.qello.filtering.domain.ProbeType;
import com.dnd.qello.filtering.domain.SnapshotHealth;
import com.dnd.qello.filtering.domain.SnapshotHealthPolicy;
import com.dnd.qello.filtering.domain.SnapshotHealthProbeResult;
import com.dnd.qello.filtering.repository.SnapshotHealthProbeResultRepository;
import com.dnd.qello.filtering.repository.SnapshotHealthRepository;

// 실사용자 요청과 분리된 synthetic target/control probe 결과를 SnapshotHealth에
// 반영한다(#109). null은 그 probe가 성공했다는 뜻이다. 두 probe 원시 결과를
// append-only로 남긴 뒤, 같은 트랜잭션에서 SnapshotHealth 집계를 갱신한다.
//
// 의도적으로 Spring 빈이 아니다 — SnapshotHealthPolicy(threshold·persistence
// window)의 실제 운영 수치가 이 이슈에서 미결정이며, probe를 실제로 주기적으로
// 실행하는 scheduler 배선은 아직 하지 않는다. #113은 활성화 게이트와 감사·지표만
// 다뤘고, 실제 배선은 그 게이트를 통과한 뒤의 후속 이슈 몫이다
// (docs/filtering-production-gate.md 3절)
// (AnswerModerationExecutionWorker, #107~#108과 동일한 이유).
public class SnapshotHealthProbeRecorder {

	private final SnapshotHealthRepository snapshotHealthRepository;
	private final SnapshotHealthProbeResultRepository probeResultRepository;
	private final SnapshotHealthPolicy policy;
	private final Clock clock;
	private final TransactionTemplate transactionTemplate;

	public SnapshotHealthProbeRecorder(
		SnapshotHealthRepository snapshotHealthRepository,
		SnapshotHealthProbeResultRepository probeResultRepository,
		SnapshotHealthPolicy policy,
		Clock clock,
		TransactionTemplate transactionTemplate
	) {
		this.snapshotHealthRepository = snapshotHealthRepository;
		this.probeResultRepository = probeResultRepository;
		this.policy = policy;
		this.clock = clock;
		this.transactionTemplate = transactionTemplate;
	}

	public SnapshotHealth recordProbe(
		String modelSnapshot, ModerationFailureClassification targetOutcome, ModerationFailureClassification controlOutcome
	) {
		return transactionTemplate.execute(status -> {
			Instant now = Instant.now(clock);
			// snapshot_health_probe_result.model_snapshot이 snapshot_health를 FK로
			// 참조하므로, 그 행을 먼저 만들고 잠근 뒤에 probe 원장을 기록해야 한다
			// (첫 probe에서는 findOrCreateForUpdate가 이 행을 새로 만든다).
			SnapshotHealth current = snapshotHealthRepository.findOrCreateForUpdate(modelSnapshot, now);
			probeResultRepository.save(
				SnapshotHealthProbeResult.of(modelSnapshot, ProbeType.TARGET, targetOutcome, now));
			probeResultRepository.save(
				SnapshotHealthProbeResult.of(modelSnapshot, ProbeType.CONTROL, controlOutcome, now));

			SnapshotHealth updated = current.recordProbe(targetOutcome, controlOutcome, now, policy);
			return snapshotHealthRepository.save(updated);
		});
	}

	public SnapshotHealth recordOfficialAnnouncement(String modelSnapshot, boolean announced) {
		return transactionTemplate.execute(status -> {
			Instant now = Instant.now(clock);
			SnapshotHealth current = snapshotHealthRepository.findOrCreateForUpdate(modelSnapshot, now);
			SnapshotHealth updated = current.recordOfficialAnnouncement(announced, now);
			return snapshotHealthRepository.save(updated);
		});
	}
}
