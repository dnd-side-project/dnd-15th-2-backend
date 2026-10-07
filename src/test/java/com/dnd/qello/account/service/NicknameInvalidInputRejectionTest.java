/*
 * Created at: 2026-10-07T11:01:09+09:00
 * Source scenario: TEST-PLAN-GH-318-NICKNAME-EMPTY-INPUT-400-UNIT-007 through UNIT-009
 */
package com.dnd.qello.account.service;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import org.assertj.core.api.ThrowableAssert.ThrowingCallable;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.transaction.PlatformTransactionManager;

import com.dnd.qello.account.config.NicknameChangeProperties;
import com.dnd.qello.account.domain.Account;
import com.dnd.qello.account.domain.AccountRole;
import com.dnd.qello.account.domain.AccountStatus;
import com.dnd.qello.account.error.AccountErrorCode;
import com.dnd.qello.account.error.AccountException;
import com.dnd.qello.account.repository.AccountRepository;
import com.dnd.qello.common.ratelimit.RateLimitPolicy;
import com.dnd.qello.filtering.domain.FilterDecision;
import com.dnd.qello.filtering.domain.FilterRelease;
import com.dnd.qello.filtering.domain.FilterReleaseStatus;
import com.dnd.qello.filtering.domain.FilterVerdict;
import com.dnd.qello.filtering.moderation.GatedNicknameModerationChecker;
import com.dnd.qello.filtering.moderation.LocalRuleVerdict;
import com.dnd.qello.filtering.moderation.ModerationLanguage;
import com.dnd.qello.filtering.moderation.ModerationPipelineService;
import com.dnd.qello.filtering.moderation.ModerationProviderClient;
import com.dnd.qello.filtering.moderation.ModerationProviderResult;
import com.dnd.qello.filtering.moderation.NicknameModerationChecker;
import com.dnd.qello.filtering.moderation.NicknameModerationOutcome;
import com.dnd.qello.filtering.moderation.NicknameModerationOutcome.Reason;
import com.dnd.qello.filtering.moderation.NicknameSyncModerationGate;
import com.dnd.qello.filtering.moderation.SecondaryModerationClient;
import com.dnd.qello.filtering.moderation.UnicodeTextNormalizer;
import com.dnd.qello.filtering.repository.FilterDecisionRepository;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

// 정규화 후 빈 닉네임(#318)이 moderation 공급자 장애(503)가 아니라 입력 오류(400)로 응답되는지 확인한다.
// #317이 NicknameRegistrationServiceTest를 함께 고치므로 충돌을 피하려고 별도 클래스로 둔다.
class NicknameInvalidInputRejectionTest {

	private static final Instant NOW = Instant.parse("2026-10-07T00:00:00Z");
	private static final long ACCOUNT_ID = 1L;
	private static final String ZWSP = String.valueOf((char) 0x200B);
	private static final String IDEOGRAPHIC_SPACE = String.valueOf((char) 0x3000);

	private static final Map<Reason, AccountErrorCode> EXPECTED_ERROR_CODES = Map.of(
			Reason.BLOCKED_BY_PRIMARY, AccountErrorCode.NICKNAME_REJECTED_BY_MODERATION,
			Reason.BLOCKED_BY_SECONDARY, AccountErrorCode.NICKNAME_REJECTED_BY_MODERATION,
			Reason.UNAVAILABLE, AccountErrorCode.NICKNAME_MODERATION_UNAVAILABLE,
			Reason.INVALID_INPUT, AccountErrorCode.REQUIRED_VALUE_MISSING);

	private AccountRepository accountRepository;
	private ExecutorService executor;

	@BeforeEach
	void setUp() {
		accountRepository = mock(AccountRepository.class);
		when(accountRepository.findById(ACCOUNT_ID)).thenReturn(Optional.of(Account.restore(
				ACCOUNT_ID, AccountRole.USER, AccountStatus.ACTIVE, "KR", "KR", "ko-KR", "Asia/Seoul", "기존닉네임", null)));
		executor = Executors.newFixedThreadPool(2);
	}

	@AfterEach
	void tearDown() {
		executor.shutdownNow();
	}

	@Test
	@DisplayName("UNIT-007: moderation이 INVALID_INPUT이면 변경·등록 경로 모두 400 REQUIRED_VALUE_MISSING(field nickname)이고 저장하지 않는다")
	void invalidInputMapsToRequiredValueMissing() {
		NicknameRegistrationService service = service(
				(nickname, language) -> NicknameModerationOutcome.rejected(Reason.INVALID_INPUT));

		assertRequiredValueMissing(() -> service.changeNickname(ACCOUNT_ID, "닉네임후보"));
		assertRequiredValueMissing(() -> service.ensureAvailable("닉네임후보", "ko-KR"));
		assertThat(AccountErrorCode.REQUIRED_VALUE_MISSING.httpStatus()).isEqualTo(HttpStatus.BAD_REQUEST);
		verify(accountRepository, never()).updateProfile(any());
	}

	@Test
	@DisplayName("UNIT-008: 모든 거절 사유가 오류 코드로 매핑되고 INVALID_INPUT만 ACC-VAL-002가 된다")
	void everyRejectionReasonMapsToAnErrorCode() {
		assertThat(EXPECTED_ERROR_CODES).containsOnlyKeys(Reason.values());

		for (Reason reason : Reason.values()) {
			NicknameRegistrationService service = service(
					(nickname, language) -> NicknameModerationOutcome.rejected(reason));

			assertThatThrownBy(() -> service.ensureAvailable("닉네임후보", "ko-KR"))
					.as("reason %s", reason)
					.isInstanceOf(AccountException.class)
					.hasFieldOrPropertyWithValue("errorCode", EXPECTED_ERROR_CODES.get(reason))
					.hasFieldOrPropertyWithValue("field", "nickname");
		}
	}

	@Test
	@DisplayName("UNIT-009: 실제 정규화기·게이트를 거치면 보이지 않는 문자만 있는 닉네임은 공급자·보조 판정기 호출 없이 400이다")
	void productionGateCompositionRejectsInvisibleOnlyNicknameAsBadRequest() {
		CountingProviderClient provider = new CountingProviderClient();
		// 보조 판정기를 ALLOW로 둔다 — 호출됐다면 닉네임이 통과해 저장까지 간다.
		CountingSecondaryClient secondary = new CountingSecondaryClient();
		NicknameRegistrationService service = service(
				new GatedNicknameModerationChecker(productionGate(provider, secondary)));

		assertRequiredValueMissing(() -> service.changeNickname(ACCOUNT_ID, ZWSP));
		assertRequiredValueMissing(() -> service.changeNickname(ACCOUNT_ID, IDEOGRAPHIC_SPACE));
		// 등록 경로(DeviceRegistrationService)는 isBlank()가 false인 닉네임을 이 메서드로 검사한다.
		assertRequiredValueMissing(() -> service.ensureAvailable(ZWSP, "ko-KR"));

		assertThat(provider.callCount).isZero();
		assertThat(secondary.callCount).isZero();
		verify(accountRepository, never()).updateProfile(any());
	}

	private static void assertRequiredValueMissing(ThrowingCallable call) {
		assertThatThrownBy(call)
				.isInstanceOf(AccountException.class)
				.hasFieldOrPropertyWithValue("errorCode", AccountErrorCode.REQUIRED_VALUE_MISSING)
				.hasFieldOrPropertyWithValue("field", "nickname");
	}

	private NicknameRegistrationService service(
			NicknameModerationChecker checker) {
		NicknameChangeProperties properties = new NicknameChangeProperties(
				Duration.ofDays(30), new RateLimitPolicy(10, Duration.ofDays(1)));
		return new NicknameRegistrationService(accountRepository, checker, properties,
				mock(PlatformTransactionManager.class), Clock.fixed(NOW, ZoneOffset.UTC));
	}

	// NicknameModerationGateConfig가 production에서 조립하는 것과 같은 정규화기·파이프라인·게이트.
	// 공급자와 보조 판정기만 test double로 바꾼다.
	private NicknameSyncModerationGate productionGate(
			ModerationProviderClient provider, SecondaryModerationClient secondary) {
		ModerationPipelineService pipeline = new ModerationPipelineService(
				new UnicodeTextNormalizer(),
				(normalizedContent, localRulesetRef) -> LocalRuleVerdict.noMatch(),
				provider,
				(providerResult, contentType, language, categoryMappingRef) -> FilterVerdict.ALLOW,
				new UnusedFilterDecisionRepository(),
				Clock.fixed(NOW, ZoneOffset.UTC));
		FilterRelease release = FilterRelease.restore(1L, UnicodeTextNormalizer.NORMALIZATION_V1, "ruleset-v1",
				"category-map-v1", "model-v1", FilterReleaseStatus.PROMOTED, NOW, NOW);
		return new NicknameSyncModerationGate(
				pipeline, secondary, executor, Duration.ofSeconds(2), Duration.ofSeconds(2), release);
	}

	private static final class CountingProviderClient implements ModerationProviderClient {
		private int callCount;

		@Override
		public ModerationProviderResult moderate(String normalizedContent, String modelSnapshot) {
			callCount++;
			return new ModerationProviderResult(false, Map.of(), Map.of(), modelSnapshot);
		}
	}

	private static final class CountingSecondaryClient implements SecondaryModerationClient {
		private int callCount;

		@Override
		public FilterVerdict moderate(String rawContent, ModerationLanguage language) {
			callCount++;
			return FilterVerdict.ALLOW;
		}
	}

	private static final class UnusedFilterDecisionRepository implements FilterDecisionRepository {
		@Override
		public FilterDecision save(FilterDecision decision) {
			throw new AssertionError("닉네임 게이트는 판정 결과를 저장하지 않아야 합니다");
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
