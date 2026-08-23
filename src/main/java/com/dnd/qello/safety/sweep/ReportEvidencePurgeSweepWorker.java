package com.dnd.qello.safety.sweep;

import java.time.Clock;
import java.time.Instant;
import java.util.List;

import org.springframework.stereotype.Service;

import com.dnd.qello.safety.domain.ReportContentSnapshot;
import com.dnd.qello.safety.error.SafetyErrorCode;
import com.dnd.qello.safety.error.SafetyException;
import com.dnd.qello.safety.repository.ReportContentSnapshotRepository;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * {@code purge_after}가 지난 증거 스냅샷의 {@code media_object_keys}를 batch로
 * 비운다(#157). 본문·해시 등 증거 자체는 여전히 절대 불변이다 —
 * {@link ReportContentSnapshotRepository#purgeMedia}가 DB 트리거가 허용하는
 * 좁은 UPDATE 하나만 실행한다. {@code legal_hold} 행과 {@code purge_after}가
 * 아직 지나지 않은 행은 {@link ReportContentSnapshotRepository#findPurgeable}
 * 조회 단계에서부터 후보에서 제외된다.
 *
 * <p>실제 오브젝트 스토리지(S3 등)에서 미디어 파일을 지우는 것은 이 클래스의
 * 책임이 아니다 — DB가 더 이상 그 미디어를 "보존 중"이라고 표시하지 않게
 * 하는 것까지만 다룬다(#157 범위).</p>
 *
 * <p>{@link com.dnd.qello.direction.sweep.RecipientExpirationSweepWorker}와
 * 같은 이유로 아무 trigger(@Scheduled 등)도 갖지 않는다. 운영 주기 실행
 * 활성화는 이 이슈의 범위 밖이다.</p>
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class ReportEvidencePurgeSweepWorker {

	private final ReportContentSnapshotRepository reportContentSnapshotRepository;
	private final Clock clock;

	public SweepBatchResult processBatch(BatchCommand command) {
		requireCommand(command);
		Instant at = resolveAt(command);
		List<ReportContentSnapshot> candidates = reportContentSnapshotRepository.findPurgeable(at, command.limit());

		int purged = 0;
		int failed = 0;
		Long firstFailedReportId = null;
		RuntimeException firstFailure = null;
		for (ReportContentSnapshot candidate : candidates) {
			try {
				reportContentSnapshotRepository.purgeMedia(candidate.reportId());
				purged++;
			} catch (RuntimeException failure) {
				// 한 행의 실패가 이미 처리된 다른 행에 영향을 주지 않는다 — 실패한
				// 행은 다음 sweep에서 다시 후보가 된다(RecipientExpirationSweepWorker와
				// 동일한 격리 방식).
				failed++;
				if (firstFailure == null) {
					firstFailedReportId = candidate.reportId();
					firstFailure = failure;
				}
			}
		}

		SweepBatchResult result = new SweepBatchResult(candidates.size(), purged, failed);
		logSummary(result, firstFailedReportId, firstFailure);
		return result;
	}

	private void logSummary(SweepBatchResult result, Long firstFailedReportId, RuntimeException firstFailure) {
		if (result.failed() == 0) {
			log.info("증거 미디어 purge sweep 완료: scanned={} purged={} failed=0",
				result.scanned(), result.purged());
			return;
		}
		log.warn("증거 미디어 purge sweep 완료: scanned={} purged={} failed={} firstFailedReportId={}",
			result.scanned(), result.purged(), result.failed(), firstFailedReportId, firstFailure);
	}

	private Instant resolveAt(BatchCommand command) {
		return command.at() == null ? clock.instant() : command.at();
	}

	private void requireCommand(BatchCommand command) {
		if (command == null) {
			throw new SafetyException(SafetyErrorCode.REQUIRED_VALUE_MISSING, "command", "command는 필수입니다");
		}
	}

	/** at이 null이면 batch 조회도 Clock의 현재 시각을 쓴다. */
	public record BatchCommand(int limit, Instant at) {
		public BatchCommand {
			if (limit <= 0) {
				throw new SafetyException(SafetyErrorCode.REQUIRED_VALUE_MISSING, "limit", "limit은 양수여야 합니다");
			}
		}
	}

	public record SweepBatchResult(int scanned, int purged, int failed) {}
}
