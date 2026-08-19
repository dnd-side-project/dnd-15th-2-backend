/*
 * Created at: 2026-08-19T03:00:00+09:00
 * Source scenario: TEST-PLAN-GH-168-NICKNAME-DUPLICATE-MODERATION-UNIT-008 through UNIT-012
 */
package com.dnd.qello.account.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DisplayName;

import com.dnd.qello.account.domain.Account;
import com.dnd.qello.account.error.AccountErrorCode;
import com.dnd.qello.account.error.AccountException;
import com.dnd.qello.account.repository.AccountRepository;
import com.dnd.qello.filtering.moderation.ModerationLanguage;
import com.dnd.qello.filtering.moderation.NicknameModerationChecker;
import com.dnd.qello.filtering.moderation.NicknameModerationOutcome;
import com.dnd.qello.filtering.moderation.NicknameModerationOutcome.Reason;

class NicknameRegistrationServiceTest {

	@Test
	@DisplayName("UNIT-008: 이미 존재하는(대소문자 다른) 닉네임이면 DUPLICATED_NICKNAME이고 moderation은 호출되지 않는다")
	void rejectsDuplicateNicknameWithoutCallingModeration() {
		FakeAccountRepository accountRepository = new FakeAccountRepository(true);
		FakeNicknameModerationChecker moderationChecker = new FakeNicknameModerationChecker(NicknameModerationOutcome.allowed());
		NicknameRegistrationService service = new NicknameRegistrationService(accountRepository, moderationChecker);

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
		FakeNicknameModerationChecker moderationChecker = new FakeNicknameModerationChecker(NicknameModerationOutcome.allowed());
		NicknameRegistrationService service = new NicknameRegistrationService(accountRepository, moderationChecker);

		assertThatThrownBy(() -> service.changeNickname(1L, "여름"))
			.isInstanceOf(AccountException.class)
			.hasFieldOrPropertyWithValue("errorCode", AccountErrorCode.DUPLICATED_NICKNAME);
	}

	@Test
	@DisplayName("앞뒤 공백만 다른 닉네임도 trim된 값 기준으로 중복 검사와 저장이 일어난다")
	void normalizesWhitespaceBeforeDuplicateCheckAndPersist() {
		FakeAccountRepository accountRepository = new FakeAccountRepository(false);
		accountRepository.store(1L, sampleAccount());
		FakeNicknameModerationChecker moderationChecker = new FakeNicknameModerationChecker(NicknameModerationOutcome.allowed());
		NicknameRegistrationService service = new NicknameRegistrationService(accountRepository, moderationChecker);

		Account updated = service.changeNickname(1L, "  새닉네임  ");

		assertThat(accountRepository.lastCheckedNickname).isEqualTo("새닉네임");
		assertThat(updated.getNickname()).isEqualTo("새닉네임");
	}

	@Test
	@DisplayName("UNIT-010: moderation이 BLOCK을 반환하면 NICKNAME_REJECTED_BY_MODERATION이고 저장하지 않는다")
	void rejectsNicknameBlockedByModeration() {
		FakeAccountRepository accountRepository = new FakeAccountRepository(false);
		accountRepository.store(1L, sampleAccount());
		FakeNicknameModerationChecker moderationChecker =
			new FakeNicknameModerationChecker(NicknameModerationOutcome.rejected(Reason.BLOCKED_BY_PRIMARY));
		NicknameRegistrationService service = new NicknameRegistrationService(accountRepository, moderationChecker);

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
		FakeNicknameModerationChecker moderationChecker =
			new FakeNicknameModerationChecker(NicknameModerationOutcome.rejected(Reason.UNAVAILABLE));
		NicknameRegistrationService service = new NicknameRegistrationService(accountRepository, moderationChecker);

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
		FakeNicknameModerationChecker moderationChecker = new FakeNicknameModerationChecker(NicknameModerationOutcome.allowed());
		NicknameRegistrationService service = new NicknameRegistrationService(accountRepository, moderationChecker);

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
		NicknameRegistrationService service = new NicknameRegistrationService(accountRepository, noOpChecker);

		service.ensureAvailable("아무닉네임", "ko-KR");
		// 예외 없이 끝나면 통과 — noOpChecker는 항상 allowed이므로 별도 검증 대상 상태가 없다.
	}

	@Test
	@DisplayName("locale이 en으로 시작하면 EN 언어로 moderation을 호출한다")
	void usesEnglishLanguageForNonKoreanLocale() {
		FakeAccountRepository accountRepository = new FakeAccountRepository(false);
		FakeNicknameModerationChecker moderationChecker = new FakeNicknameModerationChecker(NicknameModerationOutcome.allowed());
		NicknameRegistrationService service = new NicknameRegistrationService(accountRepository, moderationChecker);

		service.ensureAvailable("newNickname", "en-US");

		assertThat(moderationChecker.lastLanguage).isEqualTo(ModerationLanguage.EN);
	}

	private static Account sampleAccount() {
		return Account.restore(1L, com.dnd.qello.account.domain.AccountRole.USER,
			com.dnd.qello.account.domain.AccountStatus.ACTIVE, "KR", "KR-11", "ko-KR", "Asia/Seoul", "기존닉네임", null);
	}

	private static final class FakeAccountRepository implements AccountRepository {
		private final Map<Long, Account> accounts = new HashMap<>();
		private final boolean alwaysDuplicate;
		private int updateProfileCallCount;
		private String lastCheckedNickname;

		private FakeAccountRepository(boolean alwaysDuplicate) {
			this.alwaysDuplicate = alwaysDuplicate;
		}

		void store(long id, Account account) {
			accounts.put(id, account);
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
		public Optional<Account> findById(long id) {
			return Optional.ofNullable(accounts.get(id));
		}

		@Override
		public boolean existsActiveNickname(String nickname) {
			lastCheckedNickname = nickname;
			return alwaysDuplicate;
		}
	}

	private static final class FakeNicknameModerationChecker implements NicknameModerationChecker {
		private final NicknameModerationOutcome outcome;
		private int callCount;
		private ModerationLanguage lastLanguage;

		private FakeNicknameModerationChecker(NicknameModerationOutcome outcome) {
			this.outcome = outcome;
		}

		@Override
		public NicknameModerationOutcome check(String nickname, ModerationLanguage language) {
			callCount++;
			lastLanguage = language;
			return outcome;
		}
	}
}
