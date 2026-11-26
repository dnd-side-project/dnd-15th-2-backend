package com.dnd.qello.auth.service;

import java.time.Clock;
import java.time.Instant;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.dnd.qello.auth.domain.OperatorCredential;
import com.dnd.qello.auth.error.AuthErrorCode;
import com.dnd.qello.auth.error.AuthException;
import com.dnd.qello.auth.repository.OperatorCredentialRepository;
import com.dnd.qello.auth.security.OperatorPasswordPolicy;
import com.dnd.qello.auth.security.PasswordHasher;
import com.dnd.qello.auth.security.RawPassword;

import lombok.RequiredArgsConstructor;

// 로그인한 운영자가 자기 비밀번호를 바꾼다(#376).
//
// 검사 순서는 잠금 → 현재 비밀번호 → 작성 규칙 → 현재와 같은 값이다. 로그인과 같이 잠겨 있으면 비밀번호를 비교하지
// 않는다. 현재 비밀번호를 먼저 확인하므로 형식이 틀린 요청도 현재 비밀번호가 틀리면 실패 카운터에 더해진다.
// 순서를 바꾸면 세션을 가진 공격자가 규칙 위반 값을 붙여 카운터를 올리지 않고 현재 비밀번호를 대입할 수 있다.
//
// 세션 ID 재발급과 권한 교체는 이 트랜잭션이 커밋된 뒤 컨트롤러가 한다. 커밋 전에 세션을 바꾸면 롤백된 변경에
// 새 세션만 남는다.
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class OperatorPasswordChangeService {

	private final OperatorCredentialRepository credentialRepository;
	private final PasswordHasher passwordHasher;
	private final Clock clock;

	/**
	 * 현재 비밀번호를 확인하고 새 비밀번호로 바꾼다. 성공하면 변경 필요 상태와 실패 기록, 잠금이 함께 지워진다.
	 *
	 * @param userId
	 *            세션의 운영자 계정 id
	 */
	// noRollbackFor가 없으면 현재 비밀번호 불일치의 실패 카운터 증가가 예외와 함께 rollback되어, 이 API가 잠금 없는
	// 대입 통로가 된다. 로그인(OperatorLoginService)과 같은 이유다.
	@Transactional(noRollbackFor = AuthException.class)
	public void changePassword(long userId, RawPassword currentPassword, RawPassword newPassword) {
		Instant now = Instant.now(clock);
		OperatorCredential credential = credentialRepository.findByUserId(userId)
				.orElseThrow(() -> new AuthException(
						AuthErrorCode.CREDENTIAL_NOT_FOUND, null, "자격증명을 찾을 수 없습니다"));

		if (credential.isLockedAt(now)) {
			throw new AuthException(AuthErrorCode.CREDENTIAL_LOCKED, null, "잠금이 해제된 뒤 다시 시도해 주세요");
		}
		if (!passwordHasher.matches(currentPassword, credential.getPasswordHash())) {
			credentialRepository.updateLoginState(credential.recordFailure(now));
			throw new AuthException(
					AuthErrorCode.CURRENT_PASSWORD_MISMATCH, "currentPassword", "현재 비밀번호가 올바르지 않습니다");
		}

		OperatorPasswordPolicy.validate(credential.getLoginId(), newPassword);
		// 현재 비밀번호는 위에서 확인했으므로 평문끼리 비교한다. 해시를 한 번 더 계산할 필요가 없다.
		if (newPassword.value().equals(currentPassword.value())) {
			throw new AuthException(
					AuthErrorCode.PASSWORD_NOT_CHANGED, "newPassword", "현재 비밀번호와 다른 비밀번호를 입력해 주세요");
		}

		credentialRepository.updatePassword(credential.changePassword(passwordHasher.hash(newPassword), now));
	}
}
