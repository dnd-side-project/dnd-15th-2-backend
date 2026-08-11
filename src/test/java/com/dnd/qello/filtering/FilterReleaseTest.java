package com.dnd.qello.filtering;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Instant;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.dnd.qello.filtering.domain.FilterRelease;
import com.dnd.qello.filtering.domain.FilterReleaseStatus;
import com.dnd.qello.filtering.error.FilteringErrorCode;
import com.dnd.qello.filtering.error.FilteringException;

/**
 * Created at: 2026-08-11T00:00:00+09:00
 * Source scenario: TEST-PLAN-GH-104-RELEASE-REGISTRY-UNIT-001 through UNIT-008
 */
class FilterReleaseTest {

	private static final Instant NOW = Instant.parse("2026-08-11T00:00:00Z");

	private static FilterRelease candidate() {
		return FilterRelease.candidate("norm-v1", "ruleset-v1", "category-map-v1", "text-moderation-2026-08", NOW);
	}

	@Test
	@DisplayName("candidate는 CANDIDATE 상태로 시작하고 아직 authoritative하지 않다")
	void startsAsCandidate() {
		FilterRelease release = candidate();

		assertThat(release.status()).isEqualTo(FilterReleaseStatus.CANDIDATE);
		assertThat(release.isAuthoritative()).isFalse();
		assertThat(release.promotedAt()).isNull();
	}

	@Test
	@DisplayName("\"latest\" alias는 참조 값으로 쓸 수 없다")
	void rejectsLatestAlias() {
		assertThatThrownBy(() -> FilterRelease.candidate("latest", "ruleset-v1", "category-map-v1", "model", NOW))
			.isInstanceOf(FilteringException.class)
			.hasFieldOrPropertyWithValue("errorCode", FilteringErrorCode.LATEST_ALIAS_NOT_ALLOWED);
		assertThatThrownBy(() -> FilterRelease.candidate("norm", "ruleset", "category", " Latest ", NOW))
			.isInstanceOf(FilteringException.class)
			.hasFieldOrPropertyWithValue("errorCode", FilteringErrorCode.LATEST_ALIAS_NOT_ALLOWED);
	}

	@Test
	@DisplayName("CANDIDATE -> OFFLINE_EVALUATED -> SHADOW -> CANARY -> PROMOTED 순서로만 승격할 수 있다")
	void followsForwardPipelineOnly() {
		FilterRelease promoted = candidate()
			.markOfflineEvaluated()
			.designateShadow()
			.designateCanary()
			.promote(NOW.plusSeconds(1));

		assertThat(promoted.status()).isEqualTo(FilterReleaseStatus.PROMOTED);
		assertThat(promoted.isAuthoritative()).isTrue();
		assertThat(promoted.promotedAt()).isEqualTo(NOW.plusSeconds(1));
	}

	@Test
	@DisplayName("단계를 건너뛰면 거절된다")
	void rejectsSkippingStages() {
		FilterRelease release = candidate();

		assertThatThrownBy(release::designateShadow)
			.isInstanceOf(FilteringException.class)
			.hasFieldOrPropertyWithValue("errorCode", FilteringErrorCode.INVALID_RELEASE_STATUS);
		assertThatThrownBy(() -> release.promote(NOW))
			.isInstanceOf(FilteringException.class)
			.hasFieldOrPropertyWithValue("errorCode", FilteringErrorCode.INVALID_RELEASE_STATUS);
	}

	@Test
	@DisplayName("CANDIDATE는 markRolledBack 대상이 아니다 — PROMOTED만 ROLLED_BACK으로 내릴 수 있다")
	void onlyPromotedCanBeRolledBack() {
		FilterRelease release = candidate();

		assertThatThrownBy(release::markRolledBack)
			.isInstanceOf(FilteringException.class)
			.hasFieldOrPropertyWithValue("errorCode", FilteringErrorCode.INVALID_RELEASE_STATUS);
	}

	@Test
	@DisplayName("ROLLED_BACK release는 rePromote로 다시 PROMOTED가 될 수 있다")
	void rePromotesRolledBackRelease() {
		FilterRelease rolledBack = candidate()
			.markOfflineEvaluated()
			.designateShadow()
			.designateCanary()
			.promote(NOW.plusSeconds(1))
			.markRolledBack();

		assertThat(rolledBack.status()).isEqualTo(FilterReleaseStatus.ROLLED_BACK);
		assertThat(rolledBack.isAuthoritative()).isFalse();

		FilterRelease rePromoted = rolledBack.rePromote(NOW.plusSeconds(2));

		assertThat(rePromoted.status()).isEqualTo(FilterReleaseStatus.PROMOTED);
		assertThat(rePromoted.promotedAt()).isEqualTo(NOW.plusSeconds(2));
	}

	@Test
	@DisplayName("CANDIDATE는 rePromote 대상이 아니다 — 승격된 적 없는 release는 rollback할 수 없다")
	void rejectsRePromotingNeverPromotedRelease() {
		assertThatThrownBy(() -> candidate().rePromote(NOW))
			.isInstanceOf(FilteringException.class)
			.hasFieldOrPropertyWithValue("errorCode", FilteringErrorCode.INVALID_RELEASE_STATUS);
	}

	@Test
	@DisplayName("복원된 상태와 promotedAt의 불일치는 거절된다")
	void rejectsInconsistentRestoredState() {
		assertThatThrownBy(() -> FilterRelease.restore(1L, "norm", "ruleset", "category", "model",
			FilterReleaseStatus.PROMOTED, null, NOW))
			.isInstanceOf(FilteringException.class)
			.hasFieldOrPropertyWithValue("errorCode", FilteringErrorCode.INVALID_RELEASE_STATUS);

		assertThatThrownBy(() -> FilterRelease.restore(1L, "norm", "ruleset", "category", "model",
			FilterReleaseStatus.CANDIDATE, NOW, NOW))
			.isInstanceOf(FilteringException.class)
			.hasFieldOrPropertyWithValue("errorCode", FilteringErrorCode.INVALID_RELEASE_STATUS);
	}
}
