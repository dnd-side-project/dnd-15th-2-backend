package com.dnd.qello.auth.service;

import java.time.Instant;

import org.springframework.stereotype.Component;

import com.dnd.qello.account.service.AccountWithdrawalCleanup;
import com.dnd.qello.auth.repository.DeviceCredentialRepository;

import lombok.RequiredArgsConstructor;

// 탈퇴가 끝나면 그 사용자의 기기 자격증명을 모두 폐기한다(#337). 유예 중에는 철회를 위해 남겨 둔다.
@Component
@RequiredArgsConstructor
public class DeviceCredentialWithdrawalCleanup implements AccountWithdrawalCleanup {

	private final DeviceCredentialRepository credentialRepository;

	@Override
	public void onWithdrawalCompleted(long userId, Instant completedAt) {
		credentialRepository.revokeAllActiveByUserId(userId, completedAt);
	}

}
