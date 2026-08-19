package com.dnd.qello.account.service;

import java.util.Locale;

import org.springframework.stereotype.Service;

import com.dnd.qello.account.domain.Account;
import com.dnd.qello.account.error.AccountErrorCode;
import com.dnd.qello.account.error.AccountException;
import com.dnd.qello.account.repository.AccountRepository;
import com.dnd.qello.filtering.moderation.ModerationLanguage;
import com.dnd.qello.filtering.moderation.NicknameModerationChecker;
import com.dnd.qello.filtering.moderation.NicknameModerationOutcome;

// 닉네임 중복 검사와 moderation 판정을 한 곳에서 순서대로 실행한다(#168).
// 중복 검사(DB 읽기)가 항상 먼저다 — 이미 중복이면 moderation(외부 호출)을
// 아낀다.
//
// 이 클래스 자체는 @Transactional이 아니다. ensureAvailable은 외부 OpenAI
// 호출을 포함할 수 있으므로, 그 호출을 DB 쓰기 트랜잭션 안에 가두지 않기
// 위해서다(설계 전제는 docs/test-plans/gh-168-...md §7 참고). 닉네임 변경
// changeNickname()은 조회(읽기 전용 트랜잭션) → moderation(트랜잭션 밖) →
// 저장(AccountRepository.updateProfile의 자체 트랜잭션) 순서로, 각 단계가
// 짧은 자기 트랜잭션만 갖도록 나눴다.
//
// 등록 경로(DeviceRegistrationService.register())는 계정·자격증명 생성의
// 기존 원자성을 지키기 위해 이 서비스를 여전히 하나의 @Transactional 메서드
// 안에서 호출한다 — 그 경로에서는 moderation 호출이 트랜잭션 안에 머무는
// 트레이드오프를 그대로 받아들인다(테스트 계획 §4 위험 목록).
@Service
public class NicknameRegistrationService {

	private final AccountRepository accountRepository;
	private final NicknameModerationChecker moderationChecker;

	public NicknameRegistrationService(
		AccountRepository accountRepository,
		NicknameModerationChecker moderationChecker
	) {
		this.accountRepository = accountRepository;
		this.moderationChecker = moderationChecker;
	}

	/**
	 * 닉네임 하나를 새로 쓸 수 있는지 확인한다. 대소문자 무시 중복(자기 자신 포함)이거나
	 * moderation이 거부하면 예외를 던진다. 통과하면 아무 값도 반환하지 않는다 — 이
	 * 메서드는 검사만 하고 저장하지 않는다.
	 */
	public void ensureAvailable(String nickname, String locale) {
		// Account.validateNickname이 저장 시점에 trim하는 것과 같은 기준으로 검사해야
		// 앞뒤 공백만 다른 닉네임이 중복 검사를 우회하지 않는다(#168).
		String normalized = nickname == null ? null : nickname.trim();
		if (accountRepository.existsActiveNickname(normalized)) {
			throw new AccountException(AccountErrorCode.DUPLICATED_NICKNAME, "nickname", "이미 사용 중인 닉네임입니다");
		}

		NicknameModerationOutcome outcome = moderationChecker.check(normalized, languageOf(locale));
		if (outcome instanceof NicknameModerationOutcome.Rejected rejected) {
			throw rejectionFor(rejected.reason());
		}
	}

	/**
	 * 인증된 본인의 닉네임을 변경한다. 대상 계정이 없으면 ACCOUNT_NOT_FOUND를 던진다.
	 */
	public Account changeNickname(long accountId, String newNickname) {
		Account current = accountRepository.findById(accountId)
			.orElseThrow(() -> new AccountException(AccountErrorCode.ACCOUNT_NOT_FOUND, "id", "대상 계정이 존재하지 않습니다"));

		ensureAvailable(newNickname, current.getLocale());

		Account updated = current.updateProfile(
			current.getCoarseRegionCode(), current.getLocale(), current.getTimezone(), newNickname);
		return accountRepository.updateProfile(updated);
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
