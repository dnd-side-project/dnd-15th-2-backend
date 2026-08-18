package com.dnd.qello.account.service;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.dnd.qello.account.domain.Account;
import com.dnd.qello.account.error.AccountErrorCode;
import com.dnd.qello.account.error.AccountException;
import com.dnd.qello.account.repository.AccountRepository;
import com.dnd.qello.account.service.ProfileImageResolver.ResolvedProfileImage;
import com.dnd.qello.answer.domain.MediaAsset;
import com.dnd.qello.answer.domain.MediaAssetStatus;
import com.dnd.qello.answer.error.AnswerErrorCode;
import com.dnd.qello.answer.error.AnswerException;
import com.dnd.qello.answer.repository.MediaAssetRepository;

import lombok.RequiredArgsConstructor;

/** 본인 프로필 조회와 프로필 이미지 변경·삭제. 대상 계정은 언제나 인증 주체 자신이다. */
@Service
@RequiredArgsConstructor
public class ProfileService {

	private final AccountRepository accountRepository;
	private final MediaAssetRepository mediaAssetRepository;
	private final ProfileImageResolver profileImageResolver;

	@Transactional(readOnly = true)
	public Profile getProfile(long userId) {
		Account account = findAccount(userId);
		return new Profile(account, profileImageResolver.resolve(account));
	}

	@Transactional
	public Profile changeProfileImage(long userId, long mediaId) {
		Account account = findAccount(userId);
		requireUsableAsset(mediaId, userId);
		Account updated = accountRepository.updateProfileImage(account.withProfileImage(mediaId));
		return new Profile(updated, profileImageResolver.resolve(updated));
	}

	@Transactional
	public Profile removeProfileImage(long userId) {
		Account account = findAccount(userId);
		// 참조만 끊는다. media_asset은 다른 곳에 첨부돼 있을 수 있으므로 건드리지 않는다.
		Account updated = accountRepository.updateProfileImage(account.withoutProfileImage());
		return new Profile(updated, profileImageResolver.resolve(updated));
	}

	/**
	 * 프로필로 지정할 수 있는 자산인지 확인한다.
	 *
	 * <p>소유권을 조회 조건에 넣고, 남의 자산과 없는 자산을 모두 MEDIA_NOT_FOUND로 끊는다.
	 * 둘을 다른 코드로 구분하면 순차 증가하는 media id를 훑어 "존재하지만 내 것이 아니다"를
	 * 알아낼 수 있는 열거 오라클이 된다. MediaUploadService.confirm도 같은 이유로 구분하지
	 * 않으며, MediaAssetRepository.findById는 사용자 입력으로 호출하지 않는다.
	 *
	 * <p>READY만 허용한다. UPLOADING은 presigned PUT만 발급됐을 뿐 객체가 있다는 보장이
	 * 없고, REJECTED는 confirm 검증에 실패한 자산이며, DELETED는 terminal 상태다.
	 */
	private void requireUsableAsset(long mediaId, long ownerId) {
		MediaAsset asset = mediaAssetRepository.findByIdAndOwnerId(mediaId, ownerId)
			.orElseThrow(() -> new AnswerException(
				AnswerErrorCode.MEDIA_NOT_FOUND, "mediaId", "미디어를 찾을 수 없습니다"));
		if (asset.getStatus() != MediaAssetStatus.READY) {
			throw new AnswerException(
				AnswerErrorCode.INVALID_MEDIA_STATUS, "status", "READY 상태의 미디어만 프로필로 지정할 수 있습니다");
		}
	}

	private Account findAccount(long userId) {
		return accountRepository.findById(userId)
			.orElseThrow(() -> new AccountException(
				AccountErrorCode.ACCOUNT_NOT_FOUND, "id", "대상 계정이 존재하지 않습니다"));
	}

	public record Profile(Account account, ResolvedProfileImage profileImage) {
	}
}
