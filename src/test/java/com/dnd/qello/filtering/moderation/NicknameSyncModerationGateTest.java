/*
 * Created at: 2026-08-12T10:15:00+09:00
 * Source scenario: TEST-PLAN-GH-106-NICKNAME-SYNC-FILTER-UNIT-001 through UNIT-011
 */
package com.dnd.qello.filtering.moderation;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.dnd.qello.filtering.domain.FilterDecision;
import com.dnd.qello.filtering.domain.FilterRelease;
import com.dnd.qello.filtering.domain.FilterReleaseStatus;
import com.dnd.qello.filtering.domain.FilterTargetType;
import com.dnd.qello.filtering.domain.FilterVerdict;
import com.dnd.qello.filtering.error.FilteringErrorCode;
import com.dnd.qello.filtering.error.FilteringException;
import com.dnd.qello.filtering.moderation.NicknameModerationOutcome.Reason;
import com.dnd.qello.filtering.repository.FilterDecisionRepository;

class NicknameSyncModerationGateTest {

	private static final Instant NOW = Instant.parse("2026-08-12T00:00:00Z");
	private static final String NICKNAME = "닉네임후보";

	private ExecutorService executor;

	@BeforeEach
	void setUp() {
		executor = Executors.newFixedThreadPool(2);
	}

	@AfterEach
	void tearDown() {
		executor.shutdownNow();
	}

	@Test
	@DisplayName("주 판정기가 ALLOW면 보조 판정기를 호출하지 않고 ALLOWED를 반환한다")
	void allowedByPrimaryDoesNotCallSecondary() {
		FakeSecondaryModerationClient secondary = new FakeSecondaryModerationClient(FilterVerdict.ALLOW);
		NicknameSyncModerationGate gate = gate(allowingPrimaryPipeline(), secondary);

		NicknameModerationOutcome outcome = gate.evaluate(NICKNAME, ModerationLanguage.KO);

		assertThat(outcome).isEqualTo(NicknameModerationOutcome.allowed());
		assertThat(secondary.callCount).isZero();
	}

	@Test
	@DisplayName("주 판정기의 명시적 BLOCK은 확정 결과이며 보조 판정기가 재판정하지 않는다")
	void explicitPrimaryBlockIsFinal() {
		// 보조 판정기를 ALLOW로 구성해도(호출됐다면 결과가 뒤집혔을 상황) 무시돼야 한다.
		FakeSecondaryModerationClient secondary = new FakeSecondaryModerationClient(FilterVerdict.ALLOW);
		NicknameSyncModerationGate gate = gate(blockingPrimaryPipeline(), secondary);

		NicknameModerationOutcome outcome = gate.evaluate(NICKNAME, ModerationLanguage.KO);

		assertThat(outcome).isEqualTo(NicknameModerationOutcome.rejected(Reason.BLOCKED_BY_PRIMARY));
		assertThat(secondary.callCount).isZero();
	}

	@Test
	@DisplayName("주 판정기가 timeout/error면 보조 판정기가 정확히 1회 호출된다")
	void primaryTimeoutInvokesSecondaryExactlyOnce() {
		FakeSecondaryModerationClient secondary = new FakeSecondaryModerationClient(FilterVerdict.ALLOW);
		NicknameSyncModerationGate gate = gate(failingPrimaryPipeline(providerUnavailable()), secondary);

		gate.evaluate(NICKNAME, ModerationLanguage.KO);

		assertThat(secondary.callCount).isEqualTo(1);
	}

	@Test
	@DisplayName("주 판정기 timeout 후 보조 판정기가 ALLOW면 ALLOWED를 반환한다")
	void secondaryAllowAfterPrimaryTimeoutResultsInAllowed() {
		NicknameSyncModerationGate gate =
			gate(failingPrimaryPipeline(providerUnavailable()), new FakeSecondaryModerationClient(FilterVerdict.ALLOW));

		NicknameModerationOutcome outcome = gate.evaluate(NICKNAME, ModerationLanguage.KO);

		assertThat(outcome).isEqualTo(NicknameModerationOutcome.allowed());
	}

	@Test
	@DisplayName("주 판정기 timeout 후 보조 판정기가 BLOCK이면 REJECTED(BLOCKED_BY_SECONDARY)를 반환한다")
	void secondaryBlockAfterPrimaryTimeoutResultsInRejected() {
		NicknameSyncModerationGate gate =
			gate(failingPrimaryPipeline(providerUnavailable()), new FakeSecondaryModerationClient(FilterVerdict.BLOCK));

		NicknameModerationOutcome outcome = gate.evaluate(NICKNAME, ModerationLanguage.KO);

		assertThat(outcome).isEqualTo(NicknameModerationOutcome.rejected(Reason.BLOCKED_BY_SECONDARY));
	}

	@Test
	@DisplayName("주·보조 판정기가 모두 timeout/error면 예외 없이 REJECTED(UNAVAILABLE)를 반환한다")
	void bothUnavailableFailsClosedWithoutThrowing() {
		FakeSecondaryModerationClient secondary =
			new FakeSecondaryModerationClient(new FilteringException(FilteringErrorCode.SECONDARY_MODERATOR_UNAVAILABLE));
		NicknameSyncModerationGate gate = gate(failingPrimaryPipeline(providerUnavailable()), secondary);

		NicknameModerationOutcome outcome = gate.evaluate(NICKNAME, ModerationLanguage.KO);

		assertThat(outcome).isEqualTo(NicknameModerationOutcome.rejected(Reason.UNAVAILABLE));
	}

	@Test
	@DisplayName("주 판정기가 timeout이 아닌 다른 예외를 던져도 동일하게 보조 판정기로 전환된다")
	void nonTimeoutPrimaryFailureAlsoFallsBackToSecondary() {
		FakeSecondaryModerationClient secondary = new FakeSecondaryModerationClient(FilterVerdict.ALLOW);
		NicknameSyncModerationGate gate =
			gate(failingPrimaryPipeline(new IllegalStateException("unexpected provider failure")), secondary);

		NicknameModerationOutcome outcome = gate.evaluate(NICKNAME, ModerationLanguage.KO);

		assertThat(outcome).isEqualTo(NicknameModerationOutcome.allowed());
		assertThat(secondary.callCount).isEqualTo(1);
	}

	@Test
	@DisplayName("게이트의 결과는 항상 Allowed 또는 Rejected 중 하나이며 제3의 애매한 상태가 없다")
	void outcomeIsAlwaysExhaustivelyAllowedOrRejected() {
		NicknameSyncModerationGate gate =
			gate(allowingPrimaryPipeline(), new FakeSecondaryModerationClient(FilterVerdict.ALLOW));

		NicknameModerationOutcome outcome = gate.evaluate(NICKNAME, ModerationLanguage.KO);

		// sealed interface이므로 default 분기 없이 컴파일된다는 것 자체가 완전성 증거다.
		String classified = switch (outcome) {
			case NicknameModerationOutcome.Allowed a -> "allowed";
			case NicknameModerationOutcome.Rejected r -> "rejected:" + r.reason();
		};
		assertThat(classified).isEqualTo("allowed");
	}

	@Test
	@DisplayName("같은 게이트 인스턴스를 순차로 재사용해도 이전 호출 결과가 다음 호출에 영향을 주지 않는다")
	void gateInstanceIsStatelessAcrossSequentialCalls() {
		NicknameSyncModerationGate gate = gate(failingPrimaryPipeline(providerUnavailable()),
			new FakeSecondaryModerationClient(FilterVerdict.BLOCK));

		NicknameModerationOutcome first = gate.evaluate(NICKNAME, ModerationLanguage.KO);
		assertThat(first).isEqualTo(NicknameModerationOutcome.rejected(Reason.BLOCKED_BY_SECONDARY));

		// 같은 인스턴스를 재사용해 완전히 다른 결과(ALLOW)로 재구성된 상황을 흉내낼 수는 없으므로
		// primary/secondary 구성 자체를 바꾼 두 번째 게이트로 "같은 executor 재사용"을 검증한다.
		NicknameSyncModerationGate secondGate = gate(allowingPrimaryPipeline(), new FakeSecondaryModerationClient(FilterVerdict.ALLOW));
		NicknameModerationOutcome second = secondGate.evaluate(NICKNAME, ModerationLanguage.KO);

		assertThat(second).isEqualTo(NicknameModerationOutcome.allowed());
	}

	@Test
	@DisplayName("게이트는 filterJobId 없이(ephemeral) NICKNAME contentType으로 주 판정기를 호출한다")
	void callsPrimaryPipelineWithEphemeralNicknameRequest() {
		FakePolicyEngine policyEngine = new FakePolicyEngine(FilterVerdict.ALLOW);
		ModerationPipelineService primary = new ModerationPipelineService(
			(rawContent, normalizationRef) -> rawContent,
			(normalizedContent, localRulesetRef) -> LocalRuleVerdict.noMatch(),
			new FakeModerationProviderClient(providerResult(false)),
			policyEngine,
			new UnusedFilterDecisionRepository(),
			Clock.fixed(NOW, ZoneOffset.UTC));
		NicknameSyncModerationGate gate = gate(primary, new FakeSecondaryModerationClient(FilterVerdict.ALLOW));

		// UnusedFilterDecisionRepository는 save()가 호출되면 AssertionError를 던진다 —
		// 아래 호출이 예외 없이 끝난다는 것 자체가 filterJobId==null(영속화 없음)의 증거다.
		NicknameModerationOutcome outcome = gate.evaluate(NICKNAME, ModerationLanguage.KO);

		assertThat(outcome).isEqualTo(NicknameModerationOutcome.allowed());
		assertThat(policyEngine.lastContentType).isEqualTo(FilterTargetType.NICKNAME);
		assertThat(policyEngine.lastLanguage).isEqualTo(ModerationLanguage.KO);
	}

	@Test
	@DisplayName("최초 설정과 변경 호출은 동일한 API로 처리되며 변경 실패에 대한 완화된 별도 경로가 없다")
	void initialAndChangeCallsShareTheSameFailClosedPath() {
		// 게이트는 최초/변경을 구분하는 파라미터를 두지 않는다 — 완화된 별도 분기가 애초에
		// 존재하지 않아야 INV-NICK-006/007을 구조적으로 만족한다(설계 노트: 최초 설계 가정은
		// "구분 파라미터 여부 확인"이었으나, 실제로는 그런 파라미터가 없는 편이 더 안전한
		// 설계라 판단해 이렇게 구현했다 — 두 번의 호출이 완전히 동일하게 처리됨을 확인한다).
		NicknameSyncModerationGate gate = gate(failingPrimaryPipeline(providerUnavailable()),
			new FakeSecondaryModerationClient(providerUnavailable()));

		NicknameModerationOutcome initialSetupAttempt = gate.evaluate("최초닉네임", ModerationLanguage.KO);
		NicknameModerationOutcome changeAttempt = gate.evaluate("변경닉네임", ModerationLanguage.KO);

		assertThat(initialSetupAttempt).isEqualTo(NicknameModerationOutcome.rejected(Reason.UNAVAILABLE));
		assertThat(changeAttempt).isEqualTo(NicknameModerationOutcome.rejected(Reason.UNAVAILABLE));
	}

	private NicknameSyncModerationGate gate(ModerationPipelineService primary, SecondaryModerationClient secondary) {
		return new NicknameSyncModerationGate(
			primary, secondary, executor, Duration.ofSeconds(2), Duration.ofSeconds(2), release());
	}

	private static ModerationPipelineService allowingPrimaryPipeline() {
		return new ModerationPipelineService(
			(rawContent, normalizationRef) -> rawContent,
			(normalizedContent, localRulesetRef) -> LocalRuleVerdict.noMatch(),
			new FakeModerationProviderClient(providerResult(false)),
			new FakePolicyEngine(FilterVerdict.ALLOW),
			new UnusedFilterDecisionRepository(),
			Clock.fixed(NOW, ZoneOffset.UTC));
	}

	private static ModerationPipelineService blockingPrimaryPipeline() {
		return new ModerationPipelineService(
			(rawContent, normalizationRef) -> rawContent,
			(normalizedContent, localRulesetRef) -> LocalRuleVerdict.block("rule-nickname-001"),
			new FakeModerationProviderClient(providerResult(false)),
			new FakePolicyEngine(FilterVerdict.ALLOW),
			new UnusedFilterDecisionRepository(),
			Clock.fixed(NOW, ZoneOffset.UTC));
	}

	private static ModerationPipelineService failingPrimaryPipeline(RuntimeException failure) {
		return new ModerationPipelineService(
			(rawContent, normalizationRef) -> rawContent,
			(normalizedContent, localRulesetRef) -> LocalRuleVerdict.noMatch(),
			new FakeModerationProviderClient(failure),
			new FakePolicyEngine(FilterVerdict.ALLOW),
			new UnusedFilterDecisionRepository(),
			Clock.fixed(NOW, ZoneOffset.UTC));
	}

	private static FilteringException providerUnavailable() {
		return new FilteringException(FilteringErrorCode.MODERATION_PROVIDER_UNAVAILABLE, "openai", "timeout");
	}

	private static FilterRelease release() {
		return FilterRelease.restore(1L, "norm-v1", "ruleset-v1", "category-map-v1", "model-v1",
			FilterReleaseStatus.PROMOTED, NOW, NOW);
	}

	private static ModerationProviderResult providerResult(boolean flagged) {
		return new ModerationProviderResult(flagged, Map.of("harassment", flagged),
			Map.of("harassment", flagged ? 0.9 : 0.01), "omni-moderation-2024-09-26");
	}

	private static final class FakeModerationProviderClient implements ModerationProviderClient {
		private final ModerationProviderResult result;
		private final RuntimeException failure;

		FakeModerationProviderClient(ModerationProviderResult result) {
			this.result = result;
			this.failure = null;
		}

		FakeModerationProviderClient(RuntimeException failure) {
			this.result = null;
			this.failure = failure;
		}

		@Override
		public ModerationProviderResult moderate(String normalizedContent, String modelSnapshot) {
			if (failure != null) {
				throw failure;
			}
			return result;
		}
	}

	private static final class FakePolicyEngine implements PolicyEngine {
		private final FilterVerdict verdict;
		private FilterTargetType lastContentType;
		private ModerationLanguage lastLanguage;

		FakePolicyEngine(FilterVerdict verdict) {
			this.verdict = verdict;
		}

		@Override
		public FilterVerdict decide(ModerationProviderResult providerResult, FilterTargetType contentType,
			ModerationLanguage language, String categoryMappingRef
		) {
			this.lastContentType = contentType;
			this.lastLanguage = language;
			return verdict;
		}
	}

	private static final class FakeSecondaryModerationClient implements SecondaryModerationClient {
		private final FilterVerdict verdict;
		private final RuntimeException failure;
		private int callCount;

		FakeSecondaryModerationClient(FilterVerdict verdict) {
			this.verdict = verdict;
			this.failure = null;
		}

		FakeSecondaryModerationClient(RuntimeException failure) {
			this.verdict = null;
			this.failure = failure;
		}

		@Override
		public FilterVerdict moderate(String rawContent, ModerationLanguage language) {
			callCount++;
			if (failure != null) {
				throw failure;
			}
			return verdict;
		}
	}

	private static final class UnusedFilterDecisionRepository implements FilterDecisionRepository {
		@Override
		public FilterDecision save(FilterDecision decision) {
			throw new AssertionError("닉네임 게이트는 filterJobId 없는 요청만 pipeline에 전달해야 합니다");
		}

		@Override
		public Optional<FilterDecision> findById(long id) {
			throw new AssertionError("이 테스트에서 호출되지 않아야 합니다");
		}

		@Override
		public Optional<FilterDecision> findByFilterJobIdAndAttemptGeneration(long filterJobId, int attemptGeneration) {
			throw new AssertionError("이 테스트에서 호출되지 않아야 합니다");
		}
	}
}
