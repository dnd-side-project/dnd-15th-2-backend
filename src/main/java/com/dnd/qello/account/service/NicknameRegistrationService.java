package com.dnd.qello.account.service;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Locale;

import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

import com.dnd.qello.account.config.NicknameChangeProperties;
import com.dnd.qello.account.domain.Account;
import com.dnd.qello.account.error.AccountErrorCode;
import com.dnd.qello.account.error.AccountException;
import com.dnd.qello.account.repository.AccountRepository;
import com.dnd.qello.common.ratelimit.FixedWindowRateLimiter;
import com.dnd.qello.filtering.moderation.ModerationLanguage;
import com.dnd.qello.filtering.moderation.NicknameModerationChecker;
import com.dnd.qello.filtering.moderation.NicknameModerationOutcome;

// 닉네임 중복 검사와 moderation 판정을 한 곳에서 순서대로 실행한다(#168).
// 중복 검사(DB 읽기)가 항상 먼저다 — 이미 중복이면 moderation(외부 호출)을
// 아낀다.
//
// 닉네임 변경은 외부 OpenAI 호출을 DB 쓰기 트랜잭션 안에 가두지 않는다(설계 전제는
// docs/test-plans/gh-168-...md §7 참고). 그래서 changeNickname()은 클래스 기본 트랜잭션에
// 합류하지 않고, 저장만 TransactionTemplate으로 짧게 연다.
//
// 변경 순서는 시도 한도 → 계정 조회·변경 주기 → 정규화 검증(#317) → 중복 → moderation → 저장이다(#315). 거절할
// 요청에 moderation 비용을 쓰지 않도록 한도와 주기를 먼저 본다. 주기는 저장 트랜잭션 안에서 한 번
// 더 확인한다. moderation을 기다리는 사이 같은 사용자의 다른 변경이 먼저 저장될 수 있다.
//
// 등록 경로(DeviceRegistrationService.register())는 계정·자격증명 생성의 원자성을 지키기 위해
// ensureAvailable()을 자기 트랜잭션 안에서 호출한다 — 그 경로에서는 moderation 호출이
// 트랜잭션 안에 머무는 트레이드오프를 그대로 받아들인다(테스트 계획 §4 위험 목록).
@Service
@Transactional(readOnly = true)
public class NicknameRegistrationService {

	private final AccountRepository accountRepository;
	private final NicknameModerationChecker moderationChecker;
	private final FixedWindowRateLimiter changeAttemptLimiter;
	private final Duration changeCooldown;
	private final TransactionTemplate writeTransaction;
	private final Clock clock;

	public NicknameRegistrationService(
			AccountRepository accountRepository,
			NicknameModerationChecker moderationChecker,
			NicknameChangeProperties changeProperties,
			PlatformTransactionManager transactionManager,
			Clock clock) {
		this.accountRepository = accountRepository;
		this.moderationChecker = moderationChecker;
		this.changeAttemptLimiter = new FixedWindowRateLimiter(changeProperties.attemptRateLimit(), clock);
		this.changeCooldown = changeProperties.cooldown();
		this.writeTransaction = new TransactionTemplate(transactionManager);
		this.clock = clock;
	}

	/**
	 * 닉네임 하나를 새로 쓸 수 있는지 확인한다. 정규화한 값이 비거나 길이를 넘으면, 대소문자 무시 중복(자기 자신 포함)이거나
	 * moderation이 거부하면 예외를 던진다. 통과하면 아무 값도 반환하지 않는다 — 이 메서드는 검사만 하고 저장하지 않는다.
	 */
	public void ensureAvailable(String nickname, String locale) {
		// 저장 시점과 같은 Account.normalizeNickname 값으로 검사해야 앞뒤 공백이나 보이지 않는 문자만 다른
		// 닉네임이 중복 검사를 우회하지 않는다(#168, #317). 빈 값·길이 초과는 여기서 끝나 DB 조회와
		// moderation 호출을 하지 않는다.
		String normalized = Account.normalizeNickname(nickname);
		if (accountRepository.existsActiveNickname(normalized)) {
			throw new AccountException(AccountErrorCode.DUPLICATED_NICKNAME, "nickname", "이미 사용 중인 닉네임입니다");
		}

		NicknameModerationOutcome outcome = moderationChecker.check(normalized, languageOf(locale));
		if (outcome instanceof NicknameModerationOutcome.Rejected rejected) {
			throw rejectionFor(rejected.reason());
		}
	}

	/**
	 * 인증된 본인의 닉네임을 변경한다. 시도 한도를 넘으면 NICKNAME_CHANGE_RATE_LIMIT_EXCEEDED, 변경 주기가 지나지
	 * 않았으면 NICKNAME_CHANGE_TOO_SOON, 대상 계정이 없으면 ACCOUNT_NOT_FOUND를 던진다.
	 */
	// moderation 외부 호출을 트랜잭션 밖에 두려고 클래스 read-only 트랜잭션을 열지 않는다.
	// 저장은 writeTransaction이 따로 연다.
	@Transactional(propagation = Propagation.NOT_SUPPORTED)
	public Account changeNickname(long accountId, String newNickname) {
		if (!changeAttemptLimiter.tryAcquire(String.valueOf(accountId))) {
			throw new AccountException(
					AccountErrorCode.NICKNAME_CHANGE_RATE_LIMIT_EXCEEDED, null, "닉네임 변경 시도 한도를 넘었습니다");
		}
		Account current = findAccount(accountId);
		requireChangeAllowed(current, Instant.now(clock));

		ensureAvailable(newNickname, current.getLocale());

		return writeTransaction.execute(status -> {
			Account latest = findAccount(accountId);
			Instant changedAt = Instant.now(clock);
			requireChangeAllowed(latest, changedAt);
			return accountRepository.updateProfile(latest.changeNickname(newNickname, changedAt));
		});
	}

	private Account findAccount(long accountId) {
		return accountRepository.findById(accountId)
				.orElseThrow(() -> new AccountException(AccountErrorCode.ACCOUNT_NOT_FOUND, "id", "대상 계정이 존재하지 않습니다"));
	}

	private void requireChangeAllowed(Account account, Instant now) {
		if (!account.canChangeNicknameAt(now, changeCooldown)) {
			throw new AccountException(
					AccountErrorCode.NICKNAME_CHANGE_TOO_SOON, "nickname", "닉네임 변경 주기가 지나지 않았습니다");
		}
	}

	private static AccountException rejectionFor(NicknameModerationOutcome.Reason reason) {
		return switch (reason) {
			case BLOCKED_BY_PRIMARY, BLOCKED_BY_SECONDARY -> new AccountException(
					AccountErrorCode.NICKNAME_REJECTED_BY_MODERATION, "nickname", "닉네임이 정책을 위반했습니다");
			case UNAVAILABLE -> new AccountException(
					AccountErrorCode.NICKNAME_MODERATION_UNAVAILABLE, "nickname", "닉네임 검증 서비스를 사용할 수 없습니다");
		};
	}

	private static ModerationLanguage languageOf(String locale) {
		return locale != null && locale.toLowerCase(Locale.ROOT).startsWith("ko")
				? ModerationLanguage.KO
				: ModerationLanguage.EN;
	}
}
