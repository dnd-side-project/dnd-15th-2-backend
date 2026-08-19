package com.dnd.qello.account.repository;

import java.util.Optional;

import com.dnd.qello.account.domain.Account;

public interface AccountRepository {

	/**
	 * 신규 Account만 저장한다. account.getId()는 null이어야 한다.
	 */
	Account save(Account account);

	/**
	 * 기존 Account의 프로필(지역/로케일/타임존/닉네임)만 변경한다. account.getId()는 필수다.
	 */
	Account updateProfile(Account account);

	/**
	 * 기존 Account의 프로필 이미지 참조만 변경한다. account.getId()는 필수다.
	 * null이면 기본 이미지 상태로 되돌린다.
	 */
	Account updateProfileImage(Account account);

	/**
	 * 기존 Account의 status/deletedAt만 변경한다. account.getId()는 필수다.
	 */
	Account updateStatus(Account account);

	Optional<Account> findById(long id);

	/**
	 * 삭제되지 않은 계정 중 대소문자를 무시하고 같은 닉네임을 가진 계정이 있는지 확인한다.
	 * 자기 자신을 제외하지 않는다 — 자기 자신과의 "중복"도 같은 결과로 취급한다(#168).
	 */
	boolean existsActiveNickname(String nickname);

}
