package com.dnd.qello.direction.service;

import java.time.Instant;

import org.springframework.stereotype.Component;

import com.dnd.qello.account.service.AccountWithdrawalCleanup;
import com.dnd.qello.direction.repository.ActiveUserPresenceRepository;

import lombok.RequiredArgsConstructor;

// 탈퇴를 요청하면 마지막 위치를 지운다(#337). 매칭 후보 조회가 ACTIVE 계정만 보지만 위치는 남겨 둘 이유가 없다.
// 철회하면 앱이 위치를 다시 보낸다.
@Component
@RequiredArgsConstructor
public class PresenceWithdrawalCleanup implements AccountWithdrawalCleanup {

	private final ActiveUserPresenceRepository presenceRepository;

	@Override
	public void onWithdrawalRequested(long userId, Instant requestedAt) {
		presenceRepository.deleteByUserId(userId);
	}

}
