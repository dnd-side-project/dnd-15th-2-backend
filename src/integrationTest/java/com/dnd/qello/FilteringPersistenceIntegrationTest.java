/**
 * Created at: 2026-08-11T00:00:00+09:00
 * Source scenario: TEST-PLAN-GH-103-FILTERING-FOUNDATION-INT-001 through INT-007
 */
package com.dnd.qello;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Instant;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;

import com.dnd.qello.filtering.domain.AppealCase;
import com.dnd.qello.filtering.domain.FilterDecision;
import com.dnd.qello.filtering.domain.FilterJob;
import com.dnd.qello.filtering.domain.FilterRelease;
import com.dnd.qello.filtering.domain.FilterTarget;
import com.dnd.qello.filtering.domain.FilterTargetType;
import com.dnd.qello.filtering.domain.FilterVerdict;
import com.dnd.qello.filtering.domain.ManualReviewCase;
import com.dnd.qello.filtering.repository.AppealCaseRepository;
import com.dnd.qello.filtering.repository.FilterDecisionRepository;
import com.dnd.qello.filtering.repository.FilterJobRepository;
import com.dnd.qello.filtering.repository.FilterReleaseRepository;
import com.dnd.qello.filtering.repository.ManualReviewCaseRepository;

@SpringBootTest
@ActiveProfiles("test")
class FilteringPersistenceIntegrationTest extends PostgisContainerIntegrationTestSupport {

	private static final Instant NOW = Instant.parse("2026-08-11T00:00:00Z");
	private static final FilterTarget TARGET = FilterTarget.of(FilterTargetType.ANSWER, 1L);
	private static final Instant DEADLINE = NOW.plusSeconds(600);

	@Autowired
	private JdbcTemplate jdbc;
	@Autowired
	private FilterReleaseRepository filterReleaseRepository;
	@Autowired
	private FilterJobRepository filterJobRepository;
	@Autowired
	private FilterDecisionRepository filterDecisionRepository;
	@Autowired
	private ManualReviewCaseRepository manualReviewCaseRepository;
	@Autowired
	private AppealCaseRepository appealCaseRepository;

	private long releaseId;

	@BeforeEach
	void resetSchemaFixtures() {
		jdbc.update("DELETE FROM appeal_case");
		jdbc.update("DELETE FROM manual_review_case");
		jdbc.update("DELETE FROM filter_decision");
		jdbc.update("DELETE FROM filter_job_status_history");
		jdbc.update("DELETE FROM filter_job");
		jdbc.update("DELETE FROM filter_release");
		releaseId = filterReleaseRepository.save(
			FilterRelease.candidate("norm-v1", "ruleset-v1", "category-map-v1", "text-moderation-2026-08", NOW)).id();
	}

	@Test
	@DisplayName("FilterJob은 저장 후 조회해도 값이 그대로 보존된다")
	void savesAndFindsFilterJobThroughDomainPort() {
		FilterJob saved = filterJobRepository.save(FilterJob.create(TARGET, releaseId, "job-key-1", DEADLINE, NOW));

		FilterJob found = filterJobRepository.findById(saved.id()).orElseThrow();

		assertThat(saved.id()).isPositive();
		assertThat(found).isEqualTo(saved);
	}

	@Test
	@DisplayName("같은 idempotencyKey의 두 번째 FilterJob 저장은 DB 유일성 제약으로 거절된다")
	void rejectsDuplicateJobIdempotencyKey() {
		filterJobRepository.save(FilterJob.create(TARGET, releaseId, "dup-key", DEADLINE, NOW));

		assertThatThrownBy(() -> filterJobRepository.save(FilterJob.create(TARGET, releaseId, "dup-key", DEADLINE, NOW)))
			.isInstanceOf(DataIntegrityViolationException.class);
	}

	@Test
	@DisplayName("존재하지 않는 filterReleaseId로 FilterJob을 저장하면 FK 제약으로 거절된다")
	void rejectsFilterJobWithMissingRelease() {
		FilterJob orphan = FilterJob.create(TARGET, releaseId + 999, "orphan-key", DEADLINE, NOW);

		assertThatThrownBy(() -> filterJobRepository.save(orphan)).isInstanceOf(DataIntegrityViolationException.class);
	}

	@Test
	@DisplayName("같은 job·attempt generation의 두 번째 FilterDecision 저장은 유일성 제약으로 거절된다")
	void rejectsDuplicateDecisionForSameAttempt() {
		FilterJob job = filterJobRepository.save(FilterJob.create(TARGET, releaseId, "decision-key", DEADLINE, NOW));
		filterDecisionRepository.save(
			FilterDecision.of(job.id(), 1, FilterVerdict.ALLOW, releaseId, "text-moderation-2026-08", NOW));

		assertThatThrownBy(() -> filterDecisionRepository.save(
			FilterDecision.of(job.id(), 1, FilterVerdict.BLOCK, releaseId, "text-moderation-2026-08", NOW)))
			.isInstanceOf(DataIntegrityViolationException.class);
	}

	@Test
	@DisplayName("동일 대상·release의 ManualReviewCase는 하나만 만들 수 있고 기존 case를 조회로 찾을 수 있다")
	void enforcesManualReviewCaseUniquenessAndIdempotentLookup() {
		ManualReviewCase opened = manualReviewCaseRepository.save(ManualReviewCase.open(TARGET, releaseId, NOW));

		assertThatThrownBy(() -> manualReviewCaseRepository.save(ManualReviewCase.open(TARGET, releaseId, NOW)))
			.isInstanceOf(DataIntegrityViolationException.class);

		assertThat(manualReviewCaseRepository.findByTargetAndFilterReleaseId(TARGET, releaseId))
			.contains(opened);
	}

	@Test
	@DisplayName("동일 대상·decision의 AppealCase는 하나만 만들 수 있고 기존 appeal을 조회로 찾을 수 있다")
	void enforcesAppealCaseUniquenessAndIdempotentLookup() {
		FilterJob job = filterJobRepository.save(FilterJob.create(TARGET, releaseId, "appeal-decision-key", DEADLINE, NOW));
		FilterDecision decision = filterDecisionRepository.save(
			FilterDecision.of(job.id(), 1, FilterVerdict.BLOCK, releaseId, "text-moderation-2026-08", NOW));

		AppealCase filed = appealCaseRepository.save(
			AppealCase.file(FilterTargetType.ANSWER, TARGET.targetId(), decision.id(), NOW));

		assertThatThrownBy(() -> appealCaseRepository.save(
			AppealCase.file(FilterTargetType.ANSWER, TARGET.targetId(), decision.id(), NOW)))
			.isInstanceOf(DataIntegrityViolationException.class);

		assertThat(appealCaseRepository.findByTargetAndFilterDecisionId(
			FilterTargetType.ANSWER, TARGET.targetId(), decision.id()))
			.contains(filed);
	}

	@Test
	@DisplayName("filter_job의 RESOLVED·resolved_verdict·manually_resolved 정합성은 DB check 제약으로도 지켜진다")
	void enforcesJobStateCheckConstraintsAtDatabaseLevel() {
		assertThatThrownBy(() -> jdbc.update("""
			INSERT INTO filter_job
				(target_type, target_id, filter_release_id, status, idempotency_key, resolved_verdict)
			VALUES ('ANSWER', 1, ?, 'RESOLVED', 'raw-1', NULL)
			""", releaseId))
			.isInstanceOf(DataIntegrityViolationException.class);

		assertThatThrownBy(() -> jdbc.update("""
			INSERT INTO filter_job
				(target_type, target_id, filter_release_id, status, idempotency_key, manually_resolved)
			VALUES ('ANSWER', 1, ?, 'AUTOMATED', 'raw-2', TRUE)
			""", releaseId))
			.isInstanceOf(DataIntegrityViolationException.class);
	}
}
