/*
 * Created at: 2026-08-11T21:20:00+09:00
 * Source scenario: TEST-PLAN-GH-105-MODERATION-PIPELINE-UNIT-001 through UNIT-010,
 * TEST-PLAN-GH-105-MODERATION-PIPELINE-UNIT-013
 * (UNIT-011, UNIT-012은 OpenAI 응답 매퍼와 함께 openai 패키지에서 구현한다)
 */
package com.dnd.qello.filtering.moderation;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.dnd.qello.filtering.domain.FilterDecision;
import com.dnd.qello.filtering.domain.FilterRelease;
import com.dnd.qello.filtering.domain.FilterReleaseStatus;
import com.dnd.qello.filtering.domain.FilterTargetType;
import com.dnd.qello.filtering.domain.FilterVerdict;
import com.dnd.qello.filtering.error.FilteringErrorCode;
import com.dnd.qello.filtering.error.FilteringException;
import com.dnd.qello.filtering.repository.FilterDecisionRepository;

class ModerationPipelineServiceTest {

	private static final Instant NOW = Instant.parse("2026-08-11T00:00:00Z");
	private static final String RAW_CONTENT = "안녕하세요";

	@Test
	@DisplayName("고신뢰 로컬 규칙이 BLOCK을 반환하면 공급자를 호출하지 않는다")
	void shortCircuitsOnLocalRuleBlock() {
		FakeModerationProviderClient providerClient = new FakeModerationProviderClient(providerResult(false));
		ModerationPipelineService service = newService(new FakeTextNormalizer(),
			new FakeLocalRuleEngine(LocalRuleVerdict.block("rule-001")), providerClient,
			new FakePolicyEngine(FilterVerdict.ALLOW));

		ModerationPipelineResult result = service.execute(ephemeralRequest(release(1L)));

		assertThat(result.verdict()).isEqualTo(FilterVerdict.BLOCK);
		assertThat(result.shortCircuitedByRule()).isTrue();
		assertThat(providerClient.callCount).isZero();
	}

	@Test
	@DisplayName("로컬 규칙 미적중 시 정규화된 입력이 공급자로 전달된다")
	void callsProviderWithNormalizedInputWhenRuleDoesNotMatch() {
		FakeTextNormalizer normalizer = new FakeTextNormalizer();
		FakeModerationProviderClient providerClient = new FakeModerationProviderClient(providerResult(false));
		ModerationPipelineService service = newService(normalizer,
			new FakeLocalRuleEngine(LocalRuleVerdict.noMatch()), providerClient,
			new FakePolicyEngine(FilterVerdict.ALLOW));

		service.execute(ephemeralRequest(release(1L)));

		assertThat(providerClient.callCount).isEqualTo(1);
		assertThat(providerClient.lastNormalizedContent).isEqualTo(normalizer.lastOutput);
		assertThat(providerClient.lastNormalizedContent).isNotEqualTo(RAW_CONTENT);
	}

	@Test
	@DisplayName("공급자 flagged=true라도 정책 엔진이 ALLOW로 판단하면 최종 verdict는 ALLOW다")
	void policyEngineOverridesFlaggedTrueToAllow() {
		ModerationProviderResult flaggedButLowRisk = new ModerationProviderResult(
			true, Map.of("harassment", true), Map.of("harassment", 0.51), "omni-moderation-2024-09-26");
		ModerationPipelineService service = newService(new FakeTextNormalizer(),
			new FakeLocalRuleEngine(LocalRuleVerdict.noMatch()),
			new FakeModerationProviderClient(flaggedButLowRisk), new FakePolicyEngine(FilterVerdict.ALLOW));

		ModerationPipelineResult result = service.execute(ephemeralRequest(release(1L)));

		assertThat(result.verdict()).isEqualTo(FilterVerdict.ALLOW);
	}

	@Test
	@DisplayName("공급자 flagged=false라도 정책 엔진이 BLOCK으로 판단하면 최종 verdict는 BLOCK이다")
	void policyEngineOverridesFlaggedFalseToBlock() {
		ModerationPipelineService service = newService(new FakeTextNormalizer(),
			new FakeLocalRuleEngine(LocalRuleVerdict.noMatch()),
			new FakeModerationProviderClient(providerResult(false)), new FakePolicyEngine(FilterVerdict.BLOCK));

		ModerationPipelineResult result = service.execute(ephemeralRequest(release(1L)));

		assertThat(result.verdict()).isEqualTo(FilterVerdict.BLOCK);
	}

	@Test
	@DisplayName("공급자 timeout 시 판정으로 변환하지 않고 호출자에게 그대로 전달한다")
	void propagatesProviderTimeoutWithoutConvertingToVerdict() {
		FakePolicyEngine policyEngine = new FakePolicyEngine(FilterVerdict.ALLOW);
		FilteringException timeout = new FilteringException(
			FilteringErrorCode.MODERATION_PROVIDER_UNAVAILABLE, "openai", "timeout");
		ModerationPipelineService service = newService(new FakeTextNormalizer(),
			new FakeLocalRuleEngine(LocalRuleVerdict.noMatch()),
			new FakeModerationProviderClient(timeout), policyEngine);

		assertThatThrownBy(() -> service.execute(ephemeralRequest(release(1L))))
			.isInstanceOf(FilteringException.class)
			.satisfies(ex -> assertThat(((FilteringException) ex).getErrorCode())
				.isEqualTo(FilteringErrorCode.MODERATION_PROVIDER_UNAVAILABLE));
		assertThat(policyEngine.callCount).isZero();
	}

	@Test
	@DisplayName("공급자 서버 오류 시에도 timeout과 동일하게 판정 불가로 처리된다")
	void propagatesProviderServerErrorWithoutConvertingToVerdict() {
		FakePolicyEngine policyEngine = new FakePolicyEngine(FilterVerdict.BLOCK);
		FilteringException serverError = new FilteringException(
			FilteringErrorCode.MODERATION_PROVIDER_UNAVAILABLE, "openai", "server_error");
		ModerationPipelineService service = newService(new FakeTextNormalizer(),
			new FakeLocalRuleEngine(LocalRuleVerdict.noMatch()),
			new FakeModerationProviderClient(serverError), policyEngine);

		assertThatThrownBy(() -> service.execute(ephemeralRequest(release(1L))))
			.isInstanceOf(FilteringException.class);
		assertThat(policyEngine.callCount).isZero();
	}

	@Test
	@DisplayName("요청 release id와 공급자 응답의 실제 model이 각각 별도로 남는다")
	void keepsRequestedReleaseIdAndActualModelSeparate() {
		FilterRelease release = release(7L, "norm-v1", "requested-model-snapshot");
		ModerationProviderResult providerResult = new ModerationProviderResult(
			false, Map.of(), Map.of(), "actual-model-reported-by-provider");
		ModerationPipelineService service = newService(new FakeTextNormalizer(),
			new FakeLocalRuleEngine(LocalRuleVerdict.noMatch()),
			new FakeModerationProviderClient(providerResult), new FakePolicyEngine(FilterVerdict.ALLOW));

		ModerationPipelineResult result = service.execute(ephemeralRequest(release));

		assertThat(result.requestedReleaseId()).isEqualTo(7L);
		assertThat(result.actualModel()).isEqualTo("actual-model-reported-by-provider");
		assertThat(result.actualModel()).isNotEqualTo(release.modelSnapshot());
	}

	@Test
	@DisplayName("규칙 적중 여부, 공급자 원시 응답과 최종 정책 결정을 각각 관측할 수 있다")
	void exposesRuleProviderAndFinalDecisionSeparately() {
		ModerationProviderResult providerResult = providerResult(true);
		ModerationPipelineService service = newService(new FakeTextNormalizer(),
			new FakeLocalRuleEngine(LocalRuleVerdict.noMatch()),
			new FakeModerationProviderClient(providerResult), new FakePolicyEngine(FilterVerdict.ALLOW));

		ModerationPipelineResult result = service.execute(ephemeralRequest(release(1L)));

		assertThat(result.ruleVerdict().blocked()).isFalse();
		assertThat(result.providerResult()).isEqualTo(providerResult);
		assertThat(providerResult.flagged()).isTrue();
		assertThat(result.verdict()).isEqualTo(FilterVerdict.ALLOW);
	}

	@Test
	@DisplayName("콘텐츠 종류와 언어가 정책 엔진 입력에 그대로 전달된다")
	void propagatesContentTypeAndLanguageToPolicyEngine() {
		FakePolicyEngine policyEngine = new FakePolicyEngine(FilterVerdict.ALLOW);
		ModerationPipelineService service = newService(new FakeTextNormalizer(),
			new FakeLocalRuleEngine(LocalRuleVerdict.noMatch()),
			new FakeModerationProviderClient(providerResult(false)), policyEngine);

		service.execute(ModerationPipelineRequest.ephemeral(
			FilterTargetType.NICKNAME, ModerationLanguage.EN, "hello", release(1L)));

		assertThat(policyEngine.lastContentType).isEqualTo(FilterTargetType.NICKNAME);
		assertThat(policyEngine.lastLanguage).isEqualTo(ModerationLanguage.EN);
	}

	@Test
	@DisplayName("정규화는 release에 귀속된 normalizationRef를 사용한다")
	void normalizesUsingReleaseScopedRef() {
		FakeTextNormalizer normalizer = new FakeTextNormalizer();
		ModerationPipelineService service = newService(normalizer,
			new FakeLocalRuleEngine(LocalRuleVerdict.noMatch()),
			new FakeModerationProviderClient(providerResult(false)), new FakePolicyEngine(FilterVerdict.ALLOW));

		service.execute(ephemeralRequest(release(1L, "norm-a", "model-a")));
		assertThat(normalizer.lastNormalizationRef).isEqualTo("norm-a");

		service.execute(ephemeralRequest(release(2L, "norm-b", "model-b")));
		assertThat(normalizer.lastNormalizationRef).isEqualTo("norm-b");
	}

	@Test
	@DisplayName("서로 다른 실행 자원으로 구성된 pipeline 인스턴스는 서로의 처리량에 영향을 주지 않는다")
	void doesNotShareExecutionCapacityAcrossInstances() throws Exception {
		ExecutorService answerPathExecutor = Executors.newSingleThreadExecutor();
		try {
			CountDownLatch answerTaskStarted = new CountDownLatch(1);
			CountDownLatch releaseAnswerTask = new CountDownLatch(1);
			ModerationPipelineService answerPipeline = newService(new FakeTextNormalizer(),
				new FakeLocalRuleEngine(LocalRuleVerdict.noMatch()),
				new BlockingModerationProviderClient(answerTaskStarted, releaseAnswerTask),
				new FakePolicyEngine(FilterVerdict.ALLOW));

			Future<?> saturating =
				answerPathExecutor.submit(() -> answerPipeline.execute(ephemeralRequest(release(1L))));
			assertThat(answerTaskStarted.await(2, TimeUnit.SECONDS)).isTrue();

			ModerationPipelineService nicknamePipeline = newService(new FakeTextNormalizer(),
				new FakeLocalRuleEngine(LocalRuleVerdict.noMatch()),
				new FakeModerationProviderClient(providerResult(false)), new FakePolicyEngine(FilterVerdict.ALLOW));

			long startNanos = System.nanoTime();
			ModerationPipelineResult result = nicknamePipeline.execute(ephemeralRequest(release(2L)));
			long elapsedMillis = (System.nanoTime() - startNanos) / 1_000_000;

			assertThat(result.verdict()).isEqualTo(FilterVerdict.ALLOW);
			assertThat(elapsedMillis).isLessThan(500);

			releaseAnswerTask.countDown();
			saturating.get(2, TimeUnit.SECONDS);
		} finally {
			answerPathExecutor.shutdownNow();
		}
	}

	private static ModerationPipelineService newService(TextNormalizer normalizer, LocalRuleEngine ruleEngine,
		ModerationProviderClient providerClient, PolicyEngine policyEngine) {
		return new ModerationPipelineService(normalizer, ruleEngine, providerClient, policyEngine,
			new UnusedFilterDecisionRepository(), Clock.fixed(NOW, ZoneOffset.UTC));
	}

	private static ModerationPipelineRequest ephemeralRequest(FilterRelease release) {
		return ModerationPipelineRequest.ephemeral(FilterTargetType.ANSWER, ModerationLanguage.KO, RAW_CONTENT,
			release);
	}

	private static FilterRelease release(long id) {
		return release(id, "norm-v1", "model-v1");
	}

	private static FilterRelease release(long id, String normalizationRef, String modelSnapshot) {
		return FilterRelease.restore(id, normalizationRef, "ruleset-v1", "category-map-v1", modelSnapshot,
			FilterReleaseStatus.PROMOTED, NOW, NOW);
	}

	private static ModerationProviderResult providerResult(boolean flagged) {
		return new ModerationProviderResult(flagged, Map.of("harassment", flagged),
			Map.of("harassment", flagged ? 0.9 : 0.01), "omni-moderation-2024-09-26");
	}

	private static final class FakeTextNormalizer implements TextNormalizer {
		private String lastRawContent;
		private String lastNormalizationRef;
		private String lastOutput;

		@Override
		public String normalize(String rawContent, String normalizationRef) {
			lastRawContent = rawContent;
			lastNormalizationRef = normalizationRef;
			lastOutput = rawContent.strip() + "::" + normalizationRef;
			return lastOutput;
		}
	}

	private static final class FakeLocalRuleEngine implements LocalRuleEngine {
		private final LocalRuleVerdict verdict;

		FakeLocalRuleEngine(LocalRuleVerdict verdict) {
			this.verdict = verdict;
		}

		@Override
		public LocalRuleVerdict evaluate(String normalizedContent, String localRulesetRef) {
			return verdict;
		}
	}

	private static final class FakeModerationProviderClient implements ModerationProviderClient {
		private final ModerationProviderResult result;
		private final RuntimeException failure;
		private String lastNormalizedContent;
		private String lastModelSnapshot;
		private int callCount;

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
			callCount++;
			lastNormalizedContent = normalizedContent;
			lastModelSnapshot = modelSnapshot;
			if (failure != null) {
				throw failure;
			}
			return result;
		}
	}

	// UNIT-013 전용: 호출을 실제로 차단해 실행 자원 격리를 행위 기준으로 증명한다.
	private static final class BlockingModerationProviderClient implements ModerationProviderClient {
		private final CountDownLatch started;
		private final CountDownLatch release;

		BlockingModerationProviderClient(CountDownLatch started, CountDownLatch release) {
			this.started = started;
			this.release = release;
		}

		@Override
		public ModerationProviderResult moderate(String normalizedContent, String modelSnapshot) {
			started.countDown();
			try {
				if (!release.await(5, TimeUnit.SECONDS)) {
					throw new IllegalStateException("release latch timed out");
				}
			} catch (InterruptedException e) {
				Thread.currentThread().interrupt();
				throw new IllegalStateException(e);
			}
			return providerResult(false);
		}
	}

	private static final class FakePolicyEngine implements PolicyEngine {
		private final FilterVerdict verdict;
		private FilterTargetType lastContentType;
		private ModerationLanguage lastLanguage;
		private int callCount;

		FakePolicyEngine(FilterVerdict verdict) {
			this.verdict = verdict;
		}

		@Override
		public FilterVerdict decide(ModerationProviderResult providerResult, FilterTargetType contentType,
			ModerationLanguage language, String categoryMappingRef) {
			callCount++;
			lastContentType = contentType;
			lastLanguage = language;
			return verdict;
		}
	}

	private static final class UnusedFilterDecisionRepository implements FilterDecisionRepository {
		@Override
		public FilterDecision save(FilterDecision decision) {
			throw new AssertionError("filterJobId 없는 요청에서는 저장이 호출되지 않아야 합니다");
		}

		@Override
		public Optional<FilterDecision> findById(long id) {
			throw new UnsupportedOperationException();
		}

		@Override
		public Optional<FilterDecision> findByFilterJobIdAndAttemptGeneration(long filterJobId,
			int attemptGeneration) {
			throw new UnsupportedOperationException();
		}
	}
}
