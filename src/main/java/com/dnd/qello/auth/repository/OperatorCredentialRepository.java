package com.dnd.qello.auth.repository;

import java.util.Optional;

import com.dnd.qello.auth.domain.LoginId;
import com.dnd.qello.auth.domain.OperatorCredential;

public interface OperatorCredentialRepository {

	/**
	 * 신규 자격증명만 저장한다. 대상 계정이 OPERATOR가 아니면 DB의 복합 외래키가 거절한다.
	 */
	OperatorCredential save(OperatorCredential credential);

	/**
	 * 실패 횟수, 잠금 시각, 마지막 로그인 시각을 갱신한다. 비밀번호는 바꾸지 않는다.
	 */
	OperatorCredential updateLoginState(OperatorCredential credential);

	/**
	 * 비밀번호 해시, 변경 시각, 변경 필요 여부, 실패 횟수, 잠금 시각을 함께 갱신한다(#376). 마지막 로그인 시각은 바꾸지 않는다.
	 */
	OperatorCredential updatePassword(OperatorCredential credential);

	Optional<OperatorCredential> findByLoginId(LoginId loginId);

	Optional<OperatorCredential> findByUserId(long userId);

}
