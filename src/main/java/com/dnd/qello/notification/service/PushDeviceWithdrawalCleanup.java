package com.dnd.qello.notification.service;

import java.time.Instant;

import org.springframework.stereotype.Component;

import com.dnd.qello.account.service.AccountWithdrawalCleanup;
import com.dnd.qello.notification.repository.NotificationRepository;

import lombok.RequiredArgsConstructor;

// 탈퇴를 요청하면 그 사용자의 푸시 기기를 모두 해지한다(#337). 푸시 등록 API는 계정 상태를 보지 않아 유예 중에 다시
// 등록될 수 있으므로 탈퇴가 끝날 때 한 번 더 해지한다. 철회하면 앱이 다시 등록한다.
@Component
@RequiredArgsConstructor
public class PushDeviceWithdrawalCleanup implements AccountWithdrawalCleanup {

	private final NotificationRepository notificationRepository;

	@Override
	public void onWithdrawalRequested(long userId, Instant requestedAt) {
		notificationRepository.revokeAllDevicesByUserId(userId, requestedAt);
	}

	@Override
	public void onWithdrawalCompleted(long userId, Instant completedAt) {
		notificationRepository.revokeAllDevicesByUserId(userId, completedAt);
	}

}
