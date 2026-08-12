/*
 * Created at: 2026-08-12T10:30:00+09:00
 * Source scenario: TEST-PLAN-GH-106-NICKNAME-SYNC-FILTER-INT-001 through INT-004
 * (Spring 컨텍스트나 DB가 필요 없어 integrationTest가 아니라 src/test에 배치했다 —
 * 닉네임 게이트는 filterJobId 없이 호출되어 영속화 경로를 타지 않는다, #105/#106 설계)
 */
package com.dnd.qello.filtering.moderation;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
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
import com.dnd.qello.filtering.domain.FilterVerdict;
import com.dnd.qello.filtering.moderation.NicknameModerationOutcome.Reason;
import com.dnd.qello.filtering.repository.FilterDecisionRepository;

class NicknameSyncModerationGateConcurrencyTest {

	private static final Instant NOW = Instant.parse("2026-08-12T00:00:00Z");
	private static final String NICKNAME = "닉네임후보";

	@Test
	@DisplayName("주 판정기가 설정된 timeout보다 오래 걸리면 그 지연을 다 기다리지 않고 보조 판정기로 전환한다")
	void primaryTimeoutCutsOverToSecondaryWithoutWaitingFullDelay() throws Exception {
		ExecutorService gateExecutor = Executors.newFixedThreadPool(2);
		try {
			ModerationPipelineService slowPrimary = pipelineWithProvider(
				new SlowModerationProviderClient(Duration.ofSeconds(3), providerResult(false)));
			FakeSecondaryModerationClient secondary = new FakeSecondaryModerationClient(FilterVerdict.ALLOW);
			NicknameSyncModerationGate gate = new NicknameSyncModerationGate(
				slowPrimary, secondary, gateExecutor, Duration.ofMillis(200), Duration.ofSeconds(2), release());

			long startNanos = System.nanoTime();
			NicknameModerationOutcome outcome = gate.evaluate(NICKNAME, ModerationLanguage.KO);
			long elapsedMillis = (System.nanoTime() - startNanos) / 1_000_000;

			assertThat(outcome).isEqualTo(NicknameModerationOutcome.allowed());
			assertThat(secondary.callCount).isEqualTo(1);
			assertThat(elapsedMillis).isLessThan(1500);
		} finally {
			gateExecutor.shutdownNow();
		}
	}

	@Test
	@DisplayName("답변 경로 executor가 포화돼도 닉네임 게이트는 전용 executor로 영향 없이 완료된다")
	void answerPathExecutorSaturationDoesNotDelayNicknameGate() throws Exception {
		ExecutorService answerPathExecutor = Executors.newFixedThreadPool(1);
		ExecutorService nicknameExecutor = Executors.newFixedThreadPool(2);
		CountDownLatch answerTaskStarted = new CountDownLatch(1);
		CountDownLatch releaseAnswerTask = new CountDownLatch(1);
		try {
			Future<?> saturatingAnswerTask = answerPathExecutor.submit(() -> {
				answerTaskStarted.countDown();
				try {
					releaseAnswerTask.await(5, TimeUnit.SECONDS);
				} catch (InterruptedException e) {
					Thread.currentThread().interrupt();
				}
			});
			assertThat(answerTaskStarted.await(2, TimeUnit.SECONDS)).isTrue();

			NicknameSyncModerationGate gate = new NicknameSyncModerationGate(
				pipelineWithProvider(new FakeModerationProviderClient(providerResult(false))),
				new FakeSecondaryModerationClient(FilterVerdict.ALLOW),
				nicknameExecutor, Duration.ofSeconds(2), Duration.ofSeconds(2), release());

			long startNanos = System.nanoTime();
			NicknameModerationOutcome outcome = gate.evaluate(NICKNAME, ModerationLanguage.KO);
			long elapsedMillis = (System.nanoTime() - startNanos) / 1_000_000;

			assertThat(outcome).isEqualTo(NicknameModerationOutcome.allowed());
			assertThat(elapsedMillis).isLessThan(500);

			releaseAnswerTask.countDown();
			saturatingAnswerTask.get(2, TimeUnit.SECONDS);
		} finally {
			answerPathExecutor.shutdownNow();
			nicknameExecutor.shutdownNow();
		}
	}

	@Test
	@DisplayName("주·보조 판정기가 모두 각자 timeout보다 오래 걸려도 유한한 예산 안에서 fail-closed로 반환된다")
	void bothTimeoutsFailClosedWithinFiniteBudget() throws Exception {
		ExecutorService gateExecutor = Executors.newFixedThreadPool(2);
		try {
			ModerationPipelineService slowPrimary = pipelineWithProvider(
				new SlowModerationProviderClient(Duration.ofSeconds(3), providerResult(false)));
			SecondaryModerationClient slowSecondary = (content, language) -> {
				try {
					Thread.sleep(Duration.ofSeconds(3).toMillis());
				} catch (InterruptedException e) {
					Thread.currentThread().interrupt();
				}
				return FilterVerdict.ALLOW;
			};
			NicknameSyncModerationGate gate = new NicknameSyncModerationGate(
				slowPrimary, slowSecondary, gateExecutor, Duration.ofMillis(150), Duration.ofMillis(150), release());

			long startNanos = System.nanoTime();
			NicknameModerationOutcome outcome = gate.evaluate(NICKNAME, ModerationLanguage.KO);
			long elapsedMillis = (System.nanoTime() - startNanos) / 1_000_000;

			assertThat(outcome).isEqualTo(NicknameModerationOutcome.rejected(Reason.UNAVAILABLE));
			assertThat(elapsedMillis).isLessThan(2000);
		} finally {
			gateExecutor.shutdownNow();
		}
	}

	@Test
	@DisplayName("게이트 전용 executor 용량을 초과하는 동시 요청도 유실되거나 다른 executor로 새지 않고 모두 완료된다")
	void excessConcurrentRequestsQueueWithoutLeakingOrLoss() throws Exception {
		ExecutorService smallGateExecutor = Executors.newFixedThreadPool(2);
		ExecutorService callerPool = Executors.newFixedThreadPool(6);
		try {
			NicknameSyncModerationGate gate = new NicknameSyncModerationGate(
				pipelineWithProvider(new FakeModerationProviderClient(providerResult(false))),
				new FakeSecondaryModerationClient(FilterVerdict.ALLOW),
				smallGateExecutor, Duration.ofSeconds(2), Duration.ofSeconds(2), release());

			List<Future<NicknameModerationOutcome>> futures = new ArrayList<>();
			for (int i = 0; i < 6; i++) {
				futures.add(callerPool.submit(() -> gate.evaluate(NICKNAME, ModerationLanguage.KO)));
			}

			for (Future<NicknameModerationOutcome> future : futures) {
				assertThat(future.get(5, TimeUnit.SECONDS)).isEqualTo(NicknameModerationOutcome.allowed());
			}
		} finally {
			callerPool.shutdownNow();
			smallGateExecutor.shutdownNow();
		}
	}

	private static ModerationPipelineService pipelineWithProvider(ModerationProviderClient providerClient) {
		return new ModerationPipelineService(
			(rawContent, normalizationRef) -> rawContent,
			(normalizedContent, localRulesetRef) -> LocalRuleVerdict.noMatch(),
			providerClient,
			(providerResult, contentType, language, categoryMappingRef) -> FilterVerdict.ALLOW,
			new UnusedFilterDecisionRepository(),
			Clock.fixed(NOW, ZoneOffset.UTC));
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

		FakeModerationProviderClient(ModerationProviderResult result) {
			this.result = result;
		}

		@Override
		public ModerationProviderResult moderate(String normalizedContent, String modelSnapshot) {
			return result;
		}
	}

	private static final class SlowModerationProviderClient implements ModerationProviderClient {
		private final Duration delay;
		private final ModerationProviderResult result;

		SlowModerationProviderClient(Duration delay, ModerationProviderResult result) {
			this.delay = delay;
			this.result = result;
		}

		@Override
		public ModerationProviderResult moderate(String normalizedContent, String modelSnapshot) {
			try {
				Thread.sleep(delay.toMillis());
			} catch (InterruptedException e) {
				Thread.currentThread().interrupt();
				throw new RuntimeException("interrupted while simulating slow provider", e);
			}
			return result;
		}
	}

	private static final class FakeSecondaryModerationClient implements SecondaryModerationClient {
		private final FilterVerdict verdict;
		private int callCount;

		FakeSecondaryModerationClient(FilterVerdict verdict) {
			this.verdict = verdict;
		}

		@Override
		public FilterVerdict moderate(String rawContent, ModerationLanguage language) {
			callCount++;
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
