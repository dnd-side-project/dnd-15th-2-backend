/**
 * Created at: 2026-08-16T00:00:00+09:00
 * Source scenario: TEST-PLAN-GH-109-SNAPSHOT-HEALTH-MIGRATION-UNIT-008 through UNIT-017
 */
package com.dnd.qello.filtering;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Duration;
import java.time.Instant;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.dnd.qello.filtering.domain.ModerationFailureClassification;
import com.dnd.qello.filtering.domain.SnapshotHealth;
import com.dnd.qello.filtering.domain.SnapshotHealthPolicy;
import com.dnd.qello.filtering.domain.SnapshotHealthStatus;
import com.dnd.qello.filtering.error.FilteringErrorCode;
import com.dnd.qello.filtering.error.FilteringException;

class SnapshotHealthTest {

	private static final Instant NOW = Instant.parse("2026-08-16T00:00:00Z");
	private static final String MODEL_SNAPSHOT = "omni-moderation-2026-08-01";
	private static final SnapshotHealthPolicy POLICY = new SnapshotHealthPolicy(3, Duration.ofMinutes(5));

	@Test
	@DisplayName("HEALTHY 상태에서 target probe가 성공하면 상태를 유지하고 증거를 누적하지 않는다")
	void staysHealthyOnSuccessfulTargetProbe() {
		SnapshotHealth health = SnapshotHealth.healthy(MODEL_SNAPSHOT, NOW);

		SnapshotHealth updated = health.recordProbe(null, null, NOW.plusSeconds(1), POLICY);

		assertThat(updated.status()).isEqualTo(SnapshotHealthStatus.HEALTHY);
		assertThat(updated.targetOnlyFailureCount()).isZero();
	}

	@Test
	@DisplayName("control probe가 성공한 상태에서 target probe만 실패하면 target-only 실패로 1건 누적된다")
	void accumulatesTargetOnlyFailureWhenControlSucceeds() {
		SnapshotHealth health = SnapshotHealth.healthy(MODEL_SNAPSHOT, NOW);

		SnapshotHealth updated = health.recordProbe(
			ModerationFailureClassification.SERVER_ERROR, null, NOW.plusSeconds(1), POLICY);

		assertThat(updated.targetOnlyFailureCount()).isEqualTo(1);
		assertThat(updated.firstTargetOnlyFailureAt()).isEqualTo(NOW.plusSeconds(1));
		assertThat(updated.status()).isEqualTo(SnapshotHealthStatus.HEALTHY);
	}

	@Test
	@DisplayName("target과 control이 함께 실패하면 공급자 전역 장애로 간주해 target-only 증거로 집계하지 않는다")
	void doesNotAccumulateWhenControlAlsoFails() {
		SnapshotHealth health = SnapshotHealth.healthy(MODEL_SNAPSHOT, NOW);

		SnapshotHealth updated = health.recordProbe(ModerationFailureClassification.SERVER_ERROR,
			ModerationFailureClassification.SERVER_ERROR, NOW.plusSeconds(1), POLICY);

		assertThat(updated.targetOnlyFailureCount()).isZero();
	}

	@Test
	@DisplayName("NON_TARGET_CLIENT_ERROR는 어떤 반복 횟수에서도 target-only 증거로 집계되지 않는다")
	void neverAccumulatesNonTargetClientError() {
		SnapshotHealth health = SnapshotHealth.healthy(MODEL_SNAPSHOT, NOW);

		Instant at = NOW;
		for (int i = 0; i < 10; i++) {
			at = at.plusSeconds(1);
			health = health.recordProbe(ModerationFailureClassification.NON_TARGET_CLIENT_ERROR, null, at, POLICY);
		}

		assertThat(health.targetOnlyFailureCount()).isZero();
		assertThat(health.status()).isEqualTo(SnapshotHealthStatus.HEALTHY);
	}

	@Test
	@DisplayName("UNKNOWN 분류는 증거로 집계되지 않고 자동으로 영구 장애를 만들지 않는다")
	void neverAccumulatesUnknownClassification() {
		SnapshotHealth health = SnapshotHealth.healthy(MODEL_SNAPSHOT, NOW);

		Instant at = NOW;
		for (int i = 0; i < 10; i++) {
			at = at.plusSeconds(1);
			health = health.recordProbe(ModerationFailureClassification.UNKNOWN, null, at, POLICY);
		}

		assertThat(health.targetOnlyFailureCount()).isZero();
		assertThat(health.status()).isEqualTo(SnapshotHealthStatus.HEALTHY);
	}

	@Test
	@DisplayName("target-only 실패가 threshold 횟수와 최소 지속 시간을 모두 넘기면 PERMANENT_SUSPECTED로 전이한다")
	void transitionsToSuspectedWhenThresholdAndPersistenceBothMet() {
		SnapshotHealth health = SnapshotHealth.healthy(MODEL_SNAPSHOT, NOW);

		health = health.recordProbe(ModerationFailureClassification.SERVER_ERROR, null, NOW, POLICY);
		health = health.recordProbe(
			ModerationFailureClassification.TIMEOUT_OR_NETWORK, null, NOW.plusSeconds(60), POLICY);
		health = health.recordProbe(
			ModerationFailureClassification.SERVER_ERROR, null, NOW.plus(POLICY.minPersistence()), POLICY);

		assertThat(health.targetOnlyFailureCount()).isEqualTo(3);
		assertThat(health.status()).isEqualTo(SnapshotHealthStatus.PERMANENT_SUSPECTED);
	}

	@Test
	@DisplayName("횟수는 충족해도 최소 지속 시간을 넘기지 못하면 PERMANENT_SUSPECTED로 전이하지 않는다")
	void doesNotTransitionWhenPersistenceWindowNotMet() {
		SnapshotHealth health = SnapshotHealth.healthy(MODEL_SNAPSHOT, NOW);

		health = health.recordProbe(ModerationFailureClassification.SERVER_ERROR, null, NOW, POLICY);
		health = health.recordProbe(ModerationFailureClassification.SERVER_ERROR, null, NOW.plusSeconds(1), POLICY);
		health = health.recordProbe(ModerationFailureClassification.SERVER_ERROR, null, NOW.plusSeconds(2), POLICY);

		assertThat(health.targetOnlyFailureCount()).isEqualTo(3);
		assertThat(health.status()).isEqualTo(SnapshotHealthStatus.HEALTHY);
	}

	@Test
	@DisplayName("PERMANENT_SUSPECTED 상태에서 target probe가 성공하면 HEALTHY로 복귀하고 누적 증거가 초기화된다")
	void recoversToHealthyOnSuccessfulProbeAfterSuspected() {
		SnapshotHealth suspected = suspectedHealth();

		SnapshotHealth recovered = suspected.recordProbe(null, null, NOW.plusSeconds(100), POLICY);

		assertThat(recovered.status()).isEqualTo(SnapshotHealthStatus.HEALTHY);
		assertThat(recovered.targetOnlyFailureCount()).isZero();
		assertThat(recovered.firstTargetOnlyFailureAt()).isNull();
	}

	@Test
	@DisplayName("PERMANENT_SUSPECTED 상태에서 운영자가 confirm하면 PERMANENT_CONFIRMED로 전이하고 승인 기록을 남긴다")
	void confirmsPermanentFromSuspected() {
		SnapshotHealth suspected = suspectedHealth();

		SnapshotHealth confirmed = suspected.confirmPermanent(7L, NOW.plusSeconds(100));

		assertThat(confirmed.status()).isEqualTo(SnapshotHealthStatus.PERMANENT_CONFIRMED);
		assertThat(confirmed.confirmedByOperatorUserId()).isEqualTo(7L);
		assertThat(confirmed.confirmedAt()).isEqualTo(NOW.plusSeconds(100));
	}

	@Test
	@DisplayName("PERMANENT_SUSPECTED가 아닌 상태에서는 confirm이 거절된다")
	void rejectsConfirmWhenNotSuspected() {
		SnapshotHealth healthy = SnapshotHealth.healthy(MODEL_SNAPSHOT, NOW);

		assertThatThrownBy(() -> healthy.confirmPermanent(7L, NOW.plusSeconds(1)))
			.isInstanceOf(FilteringException.class)
			.hasFieldOrPropertyWithValue("errorCode", FilteringErrorCode.INVALID_SNAPSHOT_HEALTH_STATUS);
	}

	@Test
	@DisplayName("recordProbe와 recordOfficialAnnouncement 중 어느 것도 PERMANENT_CONFIRMED로 전이시키지 않는다")
	void onlyConfirmPermanentReachesConfirmedStatus() {
		SnapshotHealth suspected = suspectedHealth();

		SnapshotHealth afterProbeFailures = suspected;
		for (int i = 0; i < 20; i++) {
			afterProbeFailures = afterProbeFailures.recordProbe(
				ModerationFailureClassification.SERVER_ERROR, null, NOW.plusSeconds(200L + i), POLICY);
		}
		SnapshotHealth afterAnnouncement = afterProbeFailures.recordOfficialAnnouncement(true, NOW.plusSeconds(300));

		assertThat(afterAnnouncement.status()).isEqualTo(SnapshotHealthStatus.PERMANENT_SUSPECTED);
	}

	private static SnapshotHealth suspectedHealth() {
		SnapshotHealth health = SnapshotHealth.healthy(MODEL_SNAPSHOT, NOW);
		health = health.recordProbe(ModerationFailureClassification.SERVER_ERROR, null, NOW, POLICY);
		health = health.recordProbe(ModerationFailureClassification.SERVER_ERROR, null, NOW.plusSeconds(1), POLICY);
		health = health.recordProbe(
			ModerationFailureClassification.SERVER_ERROR, null, NOW.plus(POLICY.minPersistence()), POLICY);
		return health;
	}
}
