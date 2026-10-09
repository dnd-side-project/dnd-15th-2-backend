/*
 * Created at: 2026-08-19T03:00:00+09:00
 * Source scenario: TEST-PLAN-GH-168-NICKNAME-DUPLICATE-MODERATION-UNIT-008 through UNIT-012,
 * TEST-PLAN-GH-315-AUTH-NICKNAME-RATE-LIMIT-UNIT-009 through UNIT-013,
 * TEST-PLAN-GH-317-NICKNAME-INVISIBLE-CHARS-UNIT-007, UNIT-008
 */
package com.dnd.qello.account.service;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.TransactionStatus;
import org.springframework.transaction.support.SimpleTransactionStatus;

import com.dnd.qello.account.config.NicknameChangeProperties;
import com.dnd.qello.account.domain.Account;
import com.dnd.qello.account.error.AccountErrorCode;
import com.dnd.qello.account.error.AccountException;
import com.dnd.qello.account.repository.AccountRepository;
import com.dnd.qello.common.ratelimit.RateLimitPolicy;
import com.dnd.qello.filtering.moderation.ModerationLanguage;
import com.dnd.qello.filtering.moderation.NicknameModerationChecker;
import com.dnd.qello.filtering.moderation.NicknameModerationOutcome;
import com.dnd.qello.filtering.moderation.NicknameModerationOutcome.Reason;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class NicknameRegistrationServiceTest {

	private static final Instant NOW = Instant.parse("2026-10-06T05:00:00Z");
	private static final Duration COOLDOWN = Duration.ofDays(30);
	private static final int GENEROUS_ATTEMPTS = 100;
	private static final String ZWSP = Character.toString(0x200B);
	private static final String IDEOGRAPHIC_SPACE = Character.toString(0x3000);

	private final FakeTransactionManager transactionManager = new FakeTransactionManager();

	@Test
	@DisplayName("UNIT-008: 이미 존재하는(대소문자 다른) 닉네임이면 DUPLICATED_NICKNAME이고 moderation은 호출되지 않는다")
	void rejectsDuplicateNicknameWithoutCallingModeration() {
		FakeAccountRepository accountRepository = new FakeAccountRepository(true);
		FakeNicknameModerationChecker moderationChecker = new FakeNicknameModerationChecker(
				NicknameModerationOutcome.allowed());
		NicknameRegistrationService service = service(accountRepository, moderationChecker);

		assertThatThrownBy(() -> service.ensureAvailable("Summer", "ko-KR"))
				.isInstanceOf(AccountException.class)
				.hasFieldOrPropertyWithValue("errorCode", AccountErrorCode.DUPLICATED_NICKNAME);
		assertThat(moderationChecker.callCount).isZero();
	}

	@Test
	@DisplayName("UNIT-009: 자기 자신이 이미 가진 닉네임과 완전히 같은 값도 DUPLICATED_NICKNAME이다")
	void rejectsSelfDuplicateNicknameTheSameAsOtherDuplicates() {
		FakeAccountRepository accountRepository = new FakeAccountRepository(true);
		accountRepository.store(1L, Account.restore(
				1L, com.dnd.qello.account.domain.AccountRole.USER, com.dnd.qello.account.domain.AccountStatus.ACTIVE,
				"KR", "KR-11", "ko-KR", "Asia/Seoul", "여름", null));
		FakeNicknameModerationChecker moderationChecker = new FakeNicknameModerationChecker(
				NicknameModerationOutcome.allowed());
		NicknameRegistrationService service = service(accountRepository, moderationChecker);

		assertThatThrownBy(() -> service.changeNickname(1L, "여름"))
				.isInstanceOf(AccountException.class)
				.hasFieldOrPropertyWithValue("errorCode", AccountErrorCode.DUPLICATED_NICKNAME);
	}

	@Test
	@DisplayName("앞뒤 공백만 다른 닉네임도 trim된 값 기준으로 중복 검사와 저장이 일어난다")
	void normalizesWhitespaceBeforeDuplicateCheckAndPersist() {
		FakeAccountRepository accountRepository = new FakeAccountRepository(false);
		accountRepository.store(1L, sampleAccount());
		FakeNicknameModerationChecker moderationChecker = new FakeNicknameModerationChecker(
				NicknameModerationOutcome.allowed());
		NicknameRegistrationService service = service(accountRepository, moderationChecker);

		Account updated = service.changeNickname(1L, "  새닉네임  ");

		assertThat(accountRepository.lastCheckedNickname).isEqualTo("새닉네임");
		assertThat(updated.getNickname()).isEqualTo("새닉네임");
	}

	@Test
	@DisplayName("UNIT-010: moderation이 BLOCK을 반환하면 NICKNAME_REJECTED_BY_MODERATION이고 저장하지 않는다")
	void rejectsNicknameBlockedByModeration() {
		FakeAccountRepository accountRepository = new FakeAccountRepository(false);
		accountRepository.store(1L, sampleAccount());
		FakeNicknameModerationChecker moderationChecker = new FakeNicknameModerationChecker(
				NicknameModerationOutcome.rejected(Reason.BLOCKED_BY_PRIMARY));
		NicknameRegistrationService service = service(accountRepository, moderationChecker);

		assertThatThrownBy(() -> service.changeNickname(1L, "새닉네임"))
				.isInstanceOf(AccountException.class)
				.hasFieldOrPropertyWithValue("errorCode", AccountErrorCode.NICKNAME_REJECTED_BY_MODERATION);
		assertThat(accountRepository.updateProfileCallCount).isZero();
	}

	@Test
	@DisplayName("moderation이 판정 불가(UNAVAILABLE)를 반환하면 NICKNAME_MODERATION_UNAVAILABLE이고 저장하지 않는다")
	void rejectsNicknameWhenModerationUnavailable() {
		FakeAccountRepository accountRepository = new FakeAccountRepository(false);
		accountRepository.store(1L, sampleAccount());
		FakeNicknameModerationChecker moderationChecker = new FakeNicknameModerationChecker(
				NicknameModerationOutcome.rejected(Reason.UNAVAILABLE));
		NicknameRegistrationService service = service(accountRepository, moderationChecker);

		assertThatThrownBy(() -> service.changeNickname(1L, "새닉네임"))
				.isInstanceOf(AccountException.class)
				.hasFieldOrPropertyWithValue("errorCode", AccountErrorCode.NICKNAME_MODERATION_UNAVAILABLE);
		assertThat(accountRepository.updateProfileCallCount).isZero();
	}

	@Test
	@DisplayName("UNIT-011: moderation이 Allowed를 반환하면 새 닉네임으로 정확히 한 번 저장한다")
	void savesNewNicknameWhenModerationAllows() {
		FakeAccountRepository accountRepository = new FakeAccountRepository(false);
		accountRepository.store(1L, sampleAccount());
		FakeNicknameModerationChecker moderationChecker = new FakeNicknameModerationChecker(
				NicknameModerationOutcome.allowed());
		NicknameRegistrationService service = service(accountRepository, moderationChecker);

		Account updated = service.changeNickname(1L, "새닉네임");

		assertThat(updated.getNickname()).isEqualTo("새닉네임");
		assertThat(accountRepository.updateProfileCallCount).isEqualTo(1);
		assertThat(moderationChecker.lastLanguage).isEqualTo(ModerationLanguage.KO);
	}

	@Test
	@DisplayName("UNIT-012: production gate가 꺼져 있으면(NoOpNicknameModerationChecker) 중복 검사만으로 통과한다")
	void passesWithDuplicateCheckOnlyWhenGateIsNoOp() {
		FakeAccountRepository accountRepository = new FakeAccountRepository(false);
		NicknameModerationChecker noOpChecker = (nickname, language) -> NicknameModerationOutcome.allowed();
		NicknameRegistrationService service = service(accountRepository, noOpChecker);

		service.ensureAvailable("아무닉네임", "ko-KR");
		// 예외 없이 끝나면 통과 — noOpChecker는 항상 allowed이므로 별도 검증 대상 상태가 없다.
	}

	@Test
	@DisplayName("locale이 en으로 시작하면 EN 언어로 moderation을 호출한다")
	void usesEnglishLanguageForNonKoreanLocale() {
		FakeAccountRepository accountRepository = new FakeAccountRepository(false);
		FakeNicknameModerationChecker moderationChecker = new FakeNicknameModerationChecker(
				NicknameModerationOutcome.allowed());
		NicknameRegistrationService service = service(accountRepository, moderationChecker);

		service.ensureAvailable("newNickname", "en-US");

		assertThat(moderationChecker.lastLanguage).isEqualTo(ModerationLanguage.EN);
	}

	@Test
	@DisplayName("#315 UNIT-009: 변경 주기 안이면 NICKNAME_CHANGE_TOO_SOON이고 중복 검사·moderation·저장을 하지 않는다")
	void rejectsChangeInsideCooldownBeforeExternalCalls() {
		FakeAccountRepository accountRepository = new FakeAccountRepository(false);
		accountRepository.store(1L, sampleAccount().withNicknameChangedAt(NOW.minus(Duration.ofDays(1))));
		FakeNicknameModerationChecker moderationChecker = new FakeNicknameModerationChecker(
				NicknameModerationOutcome.allowed());
		NicknameRegistrationService service = service(accountRepository, moderationChecker);

		assertThatThrownBy(() -> service.changeNickname(1L, "새닉네임"))
				.isInstanceOf(AccountException.class)
				.hasFieldOrPropertyWithValue("errorCode", AccountErrorCode.NICKNAME_CHANGE_TOO_SOON);
		assertThat(accountRepository.lastCheckedNickname).isNull();
		assertThat(moderationChecker.callCount).isZero();
		assertThat(accountRepository.updateProfileCallCount).isZero();
		assertThat(transactionManager.begun).isZero();
	}

	@Test
	@DisplayName("#315 UNIT-009: moderation을 기다리는 사이 다른 변경이 저장됐으면 저장 트랜잭션 안에서 다시 거절한다")
	void rechecksCooldownInsideWriteTransaction() {
		FakeAccountRepository accountRepository = new FakeAccountRepository(false);
		accountRepository.queueReads(sampleAccount(), sampleAccount().changeNickname("먼저바꾼닉네임", NOW));
		FakeNicknameModerationChecker moderationChecker = new FakeNicknameModerationChecker(
				NicknameModerationOutcome.allowed());
		NicknameRegistrationService service = service(accountRepository, moderationChecker);

		assertThatThrownBy(() -> service.changeNickname(1L, "새닉네임"))
				.isInstanceOf(AccountException.class)
				.hasFieldOrPropertyWithValue("errorCode", AccountErrorCode.NICKNAME_CHANGE_TOO_SOON);
		assertThat(moderationChecker.callCount).isEqualTo(1);
		assertThat(accountRepository.updateProfileCallCount).isZero();
		assertThat(transactionManager.rolledBack).isEqualTo(1);
		assertThat(transactionManager.committed).isZero();
	}

	@Test
	@DisplayName("#315 UNIT-010: 시도 한도를 넘으면 NICKNAME_CHANGE_RATE_LIMIT_EXCEEDED이고 계정 조회·moderation·저장을 하지 않는다")
	void rejectsWhenAttemptLimitExceeded() {
		FakeAccountRepository accountRepository = new FakeAccountRepository(false);
		accountRepository.store(1L, sampleAccount());
		FakeNicknameModerationChecker moderationChecker = new FakeNicknameModerationChecker(
				NicknameModerationOutcome.rejected(Reason.BLOCKED_BY_PRIMARY));
		NicknameRegistrationService service = service(accountRepository, moderationChecker, 2);
		attemptAndIgnore(service, 1L, "첫시도");
		attemptAndIgnore(service, 1L, "두번째시도");
		int readsBefore = accountRepository.findByIdCallCount;

		assertThatThrownBy(() -> service.changeNickname(1L, "세번째시도"))
				.isInstanceOf(AccountException.class)
				.hasFieldOrPropertyWithValue("errorCode", AccountErrorCode.NICKNAME_CHANGE_RATE_LIMIT_EXCEEDED);
		assertThat(accountRepository.findByIdCallCount).isEqualTo(readsBefore);
		assertThat(moderationChecker.callCount).isEqualTo(2);
		assertThat(accountRepository.updateProfileCallCount).isZero();
	}

	@Test
	@DisplayName("#315 UNIT-010: 시도 한도는 사용자별로 따로 센다")
	void countsAttemptsPerUser() {
		FakeAccountRepository accountRepository = new FakeAccountRepository(false);
		accountRepository.store(1L, sampleAccount());
		accountRepository.store(2L, Account.restore(2L, com.dnd.qello.account.domain.AccountRole.USER,
				com.dnd.qello.account.domain.AccountStatus.ACTIVE, "KR", "KR-11", "ko-KR", "Asia/Seoul", "둘째", null));
		FakeNicknameModerationChecker moderationChecker = new FakeNicknameModerationChecker(
				NicknameModerationOutcome.allowed());
		NicknameRegistrationService service = service(accountRepository, moderationChecker, 1);
		service.changeNickname(1L, "첫사용자닉네임");

		Account second = service.changeNickname(2L, "둘째사용자닉네임");

		assertThat(second.getNickname()).isEqualTo("둘째사용자닉네임");
	}

	@Test
	@DisplayName("#315 UNIT-011: 변경에 성공하면 Clock의 현재 시각을 마지막 변경 시각으로 저장하고 쓰기 트랜잭션을 커밋한다")
	void recordsChangedAtOnSuccess() {
		FakeAccountRepository accountRepository = new FakeAccountRepository(false);
		accountRepository.store(1L, sampleAccount());
		FakeNicknameModerationChecker moderationChecker = new FakeNicknameModerationChecker(
				NicknameModerationOutcome.allowed());
		NicknameRegistrationService service = service(accountRepository, moderationChecker);

		Account updated = service.changeNickname(1L, "새닉네임");

		assertThat(updated.getNicknameChangedAt()).isEqualTo(NOW);
		assertThat(accountRepository.findById(1L)).get()
				.extracting(Account::getNicknameChangedAt).isEqualTo(NOW);
		assertThat(transactionManager.committed).isEqualTo(1);
	}

	@Test
	@DisplayName("#315 UNIT-012: 중복·moderation 판정 불가로 실패한 시도도 한도에 들어가고 변경 주기는 시작되지 않는다")
	void failedAttemptsCountButDoNotStartCooldown() {
		FakeAccountRepository accountRepository = new FakeAccountRepository(true);
		accountRepository.store(1L, sampleAccount());
		FakeNicknameModerationChecker moderationChecker = new FakeNicknameModerationChecker(
				NicknameModerationOutcome.rejected(Reason.UNAVAILABLE));
		NicknameRegistrationService service = service(accountRepository, moderationChecker, 2);

		assertThatThrownBy(() -> service.changeNickname(1L, "중복닉네임"))
				.hasFieldOrPropertyWithValue("errorCode", AccountErrorCode.DUPLICATED_NICKNAME);
		accountRepository.alwaysDuplicate = false;
		assertThatThrownBy(() -> service.changeNickname(1L, "새닉네임"))
				.hasFieldOrPropertyWithValue("errorCode", AccountErrorCode.NICKNAME_MODERATION_UNAVAILABLE);
		assertThatThrownBy(() -> service.changeNickname(1L, "새닉네임"))
				.hasFieldOrPropertyWithValue("errorCode", AccountErrorCode.NICKNAME_CHANGE_RATE_LIMIT_EXCEEDED);

		assertThat(accountRepository.findById(1L)).get()
				.extracting(Account::getNicknameChangedAt).isNull();
		assertThat(accountRepository.updateProfileCallCount).isZero();
	}

	@Test
	@DisplayName("#315 UNIT-013: 등록 경로의 ensureAvailable은 변경 시도 한도와 무관하게 검사를 수행한다")
	void ensureAvailableIgnoresChangeAttemptLimit() {
		FakeAccountRepository accountRepository = new FakeAccountRepository(false);
		accountRepository.store(1L, sampleAccount());
		FakeNicknameModerationChecker moderationChecker = new FakeNicknameModerationChecker(
				NicknameModerationOutcome.allowed());
		NicknameRegistrationService service = service(accountRepository, moderationChecker, 1);
		service.changeNickname(1L, "새닉네임");

		service.ensureAvailable("가입닉네임", "ko-KR");
		service.ensureAvailable("가입닉네임2", "ko-KR");

		assertThat(moderationChecker.callCount).isEqualTo(3);
	}

	@Test
	@DisplayName("#317 UNIT-007: 중복 검사 입력·moderation 입력·저장값이 모두 보이지 않는 문자와 공백을 정규화한 같은 값이다")
	void usesOneNormalizedNicknameForDuplicateCheckModerationAndPersist() {
		FakeAccountRepository accountRepository = new FakeAccountRepository(false);
		accountRepository.store(1L, sampleAccount());
		FakeNicknameModerationChecker moderationChecker = new FakeNicknameModerationChecker(
				NicknameModerationOutcome.allowed());
		NicknameRegistrationService service = service(accountRepository, moderationChecker);

		Account updated = service.changeNickname(1L, IDEOGRAPHIC_SPACE + "바" + ZWSP + "람 ");

		assertThat(accountRepository.lastCheckedNickname).isEqualTo("바람");
		assertThat(moderationChecker.lastNickname).isEqualTo("바람");
		assertThat(updated.getNickname()).isEqualTo("바람");
		assertThat(accountRepository.updateProfileCallCount).isEqualTo(1);
	}

	@Test
	@DisplayName("#317 UNIT-008: 정규화하면 비는 닉네임은 REQUIRED_VALUE_MISSING이고 중복 검사·moderation·저장을 하지 않는다")
	void rejectsNicknameThatBecomesEmptyBeforeDuplicateCheckAndModeration() {
		FakeAccountRepository accountRepository = new FakeAccountRepository(false);
		accountRepository.store(1L, sampleAccount());
		FakeNicknameModerationChecker moderationChecker = new FakeNicknameModerationChecker(
				NicknameModerationOutcome.allowed());
		NicknameRegistrationService service = service(accountRepository, moderationChecker);

		assertThatThrownBy(() -> service.changeNickname(1L, ZWSP))
				.isInstanceOf(AccountException.class)
				.hasFieldOrPropertyWithValue("errorCode", AccountErrorCode.REQUIRED_VALUE_MISSING);
		assertThat(accountRepository.lastCheckedNickname).isNull();
		assertThat(moderationChecker.callCount).isZero();
		assertThat(accountRepository.updateProfileCallCount).isZero();
	}

	@Test
	@DisplayName("#317 UNIT-008: 50자를 넘는 닉네임은 TEXT_TOO_LONG이고 중복 검사·moderation·저장을 하지 않는다")
	void rejectsTooLongNicknameBeforeDuplicateCheckAndModeration() {
		FakeAccountRepository accountRepository = new FakeAccountRepository(false);
		accountRepository.store(1L, sampleAccount());
		FakeNicknameModerationChecker moderationChecker = new FakeNicknameModerationChecker(
				NicknameModerationOutcome.allowed());
		NicknameRegistrationService service = service(accountRepository, moderationChecker);

		assertThatThrownBy(() -> service.changeNickname(1L, "가".repeat(51)))
				.isInstanceOf(AccountException.class)
				.hasFieldOrPropertyWithValue("errorCode", AccountErrorCode.TEXT_TOO_LONG);
		assertThat(accountRepository.lastCheckedNickname).isNull();
		assertThat(moderationChecker.callCount).isZero();
		assertThat(accountRepository.updateProfileCallCount).isZero();
	}

	private NicknameRegistrationService service(AccountRepository accountRepository,
			NicknameModerationChecker checker) {
		return service(accountRepository, checker, GENEROUS_ATTEMPTS);
	}

	private NicknameRegistrationService service(
			AccountRepository accountRepository, NicknameModerationChecker checker, int maxAttempts) {
		NicknameChangeProperties properties = new NicknameChangeProperties(COOLDOWN,
				new RateLimitPolicy(maxAttempts, Duration.ofDays(1)));
		return new NicknameRegistrationService(
				accountRepository, checker, properties, transactionManager, Clock.fixed(NOW, ZoneOffset.UTC));
	}

	private static void attemptAndIgnore(NicknameRegistrationService service, long accountId, String nickname) {
		try {
			service.changeNickname(accountId, nickname);
		} catch (AccountException ignored) {
			// 한도 계산만 확인하므로 개별 실패 사유는 검증하지 않는다.
		}
	}

	private static Account sampleAccount() {
		return Account.restore(1L, com.dnd.qello.account.domain.AccountRole.USER,
				com.dnd.qello.account.domain.AccountStatus.ACTIVE, "KR", "KR-11", "ko-KR", "Asia/Seoul", "기존닉네임", null);
	}

	private static final class FakeAccountRepository implements AccountRepository {
		private final Map<Long, Account> accounts = new HashMap<>();
		private final Deque<Account> queuedReads = new ArrayDeque<>();
		private boolean alwaysDuplicate;
		private int updateProfileCallCount;
		private int findByIdCallCount;
		private String lastCheckedNickname;

		private FakeAccountRepository(boolean alwaysDuplicate) {
			this.alwaysDuplicate = alwaysDuplicate;
		}

		void store(long id, Account account) {
			accounts.put(id, account);
		}

		// findById가 차례로 돌려줄 계정. 다 쓰면 store한 값을 돌려준다.
		void queueReads(Account... reads) {
			queuedReads.addAll(List.of(reads));
		}

		@Override
		public Account save(Account account) {
			throw new UnsupportedOperationException();
		}

		@Override
		public Account updateProfile(Account account) {
			updateProfileCallCount++;
			accounts.put(account.getId(), account);
			return account;
		}

		@Override
		public Account updateProfileImage(Account account) {
			throw new UnsupportedOperationException();
		}

		@Override
		public Account updateStatus(Account account) {
			throw new UnsupportedOperationException();
		}

		@Override
		public Account updateDeletion(Account account) {
			throw new UnsupportedOperationException();
		}

		@Override
		public Optional<Account> findById(long id) {
			findByIdCallCount++;
			if (!queuedReads.isEmpty()) {
				return Optional.of(queuedReads.poll());
			}
			return Optional.ofNullable(accounts.get(id));
		}

		@Override
		public boolean existsActiveNickname(String nickname) {
			lastCheckedNickname = nickname;
			return alwaysDuplicate;
		}

		@Override
		public List<Long> findWithdrawalDueIds(Instant requestedAtOrBefore, int limit) {
			throw new UnsupportedOperationException();
		}
	}

	private static final class FakeNicknameModerationChecker implements NicknameModerationChecker {
		private final NicknameModerationOutcome outcome;
		private int callCount;
		private String lastNickname;
		private ModerationLanguage lastLanguage;

		private FakeNicknameModerationChecker(NicknameModerationOutcome outcome) {
			this.outcome = outcome;
		}

		@Override
		public NicknameModerationOutcome check(String nickname, ModerationLanguage language) {
			callCount++;
			lastNickname = nickname;
			lastLanguage = language;
			return outcome;
		}
	}

	private static final class FakeTransactionManager implements PlatformTransactionManager {
		private int begun;
		private int committed;
		private int rolledBack;

		@Override
		public TransactionStatus getTransaction(TransactionDefinition definition) {
			begun++;
			return new SimpleTransactionStatus();
		}

		@Override
		public void commit(TransactionStatus status) {
			committed++;
		}

		@Override
		public void rollback(TransactionStatus status) {
			rolledBack++;
		}
	}
}
