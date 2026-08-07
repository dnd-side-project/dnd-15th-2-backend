package com.dnd.qello.auth.service;

import java.time.Clock;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.dnd.qello.account.domain.Account;
import com.dnd.qello.account.domain.AccountStatus;
import com.dnd.qello.account.repository.AccountRepository;
import com.dnd.qello.auth.domain.LoginId;
import com.dnd.qello.auth.domain.OperatorCredential;
import com.dnd.qello.auth.error.AuthErrorCode;
import com.dnd.qello.auth.error.AuthException;
import com.dnd.qello.auth.repository.OperatorCredentialRepository;
import com.dnd.qello.auth.security.PasswordHash;
import com.dnd.qello.auth.security.PasswordHasher;
import com.dnd.qello.auth.security.RawPassword;

// 백오피스 로그인 판정.
//
// 계정 열거를 막는 것이 이 클래스의 핵심 제약이다. 존재하지 않는 login_id와 잘못된
// 비밀번호는 같은 코드(AUT-APP-001)로 나가야 하고, 응답 시간도 구분되면 안 된다.
// 그래서 자격증명이 없을 때도 더미 해시로 검증을 한 번 수행한다.
@Service
@Transactional(readOnly = true)
public class OperatorLoginService {

	private final OperatorCredentialRepository credentialRepository;
	private final AccountRepository accountRepository;
	private final PasswordHasher passwordHasher;
	private final Clock clock;

	// 존재하지 않는 login_id의 응답 시간을 맞추기 위한 더미 해시.
	//
	// 상수로 박아 두지 않는다. 형식이 어긋나면 BCryptPasswordEncoder가 비교를 건너뛰고
	// 즉시 false를 돌려주어 방어가 조용히 사라지고, hasher의 cost factor가 바뀌면
	// 실제 검증과 비용도 달라진다. 기동 시 한 번 만들어 두면 두 문제가 함께 없어진다.
	private final PasswordHash dummyHash;

	public OperatorLoginService(
		OperatorCredentialRepository credentialRepository,
		AccountRepository accountRepository,
		PasswordHasher passwordHasher,
		Clock clock
	) {
		this.credentialRepository = credentialRepository;
		this.accountRepository = accountRepository;
		this.passwordHasher = passwordHasher;
		this.clock = clock;
		this.dummyHash = passwordHasher.hash(new RawPassword(UUID.randomUUID().toString()));
	}

	/**
	 * 자격증명을 검증하고 로그인한 운영자의 계정 id를 돌려준다.
	 *
	 * <p>실패 원인은 호출자에게 구분해 알리지 않는다.
	 */
	// noRollbackFor가 없으면 실패 카운터 증가가 예외와 함께 rollback되어 잠금이 영원히
	// 걸리지 않는다. 로그인 실패는 "실패를 기록하고 거절한다"는 하나의 성공한 처리다.
	@Transactional(noRollbackFor = AuthException.class)
	public long login(LoginId loginId, RawPassword rawPassword) {
		Instant now = Instant.now(clock);
		Optional<OperatorCredential> found = credentialRepository.findByLoginId(loginId);

		if (found.isEmpty()) {
			// 계정이 없어도 같은 비용을 치른다. 이 호출을 지우면 응답 시간으로 계정 존재를
			// 알아낼 수 있다.
			passwordHasher.matches(rawPassword, dummyHash);
			throw loginFailed();
		}

		OperatorCredential credential = found.get();
		if (credential.isLockedAt(now)) {
			throw new AuthException(
				AuthErrorCode.CREDENTIAL_LOCKED, null, "잠금이 해제된 뒤 다시 시도해 주세요");
		}

		if (!passwordHasher.matches(rawPassword, credential.getPasswordHash())) {
			credentialRepository.updateLoginState(credential.recordFailure(now));
			throw loginFailed();
		}

		requireActiveAccount(credential.getUserId());
		credentialRepository.updateLoginState(credential.recordSuccess(now));
		return credential.getUserId();
	}

	// 차단·삭제된 계정은 자격증명이 맞아도 로그인시키지 않는다. 여기서 막지 않으면
	// 계정 상태 변경이 세션에 반영되지 않는다.
	private void requireActiveAccount(long userId) {
		Account account = accountRepository.findById(userId)
			.orElseThrow(this::loginFailed);
		if (account.getStatus() != AccountStatus.ACTIVE) {
			throw new AuthException(
				AuthErrorCode.ACCOUNT_NOT_ACTIVE, null, "사용할 수 없는 계정입니다");
		}
	}

	private AuthException loginFailed() {
		// field와 reason에 입력값을 담지 않는다. 어느 쪽이 틀렸는지 노출된다.
		return new AuthException(AuthErrorCode.LOGIN_FAILED, null, "로그인 정보가 올바르지 않습니다");
	}

}
